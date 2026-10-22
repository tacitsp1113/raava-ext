const axios = require('axios');

const TMDB_KEY = process.env.TMDB_KEY || '15d2ea6d0dc1d476efbca3eba2b9bbfb';
const cache = new Map();

/**
 * Normalizes title for comparison
 */
function cleanTitle(str) {
  if (!str) return '';
  return str
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Fetches media details by IMDb ID using TMDB (with Cinemeta fallback)
 */
async function getMediaDetails(type, imdbId) {
  const cacheKey = `${type}:${imdbId}`;
  if (cache.has(cacheKey)) {
    return cache.get(cacheKey);
  }

  try {
    const tmdbUrl = `https://api.themoviedb.org/3/find/${imdbId}?api_key=${TMDB_KEY}&external_source=imdb_id&language=es-MX`;
    const res = await axios.get(tmdbUrl, { timeout: 8000 });
    const data = res.data;

    let result = null;

    if (type === 'movie' && data.movie_results && data.movie_results.length > 0) {
      const m = data.movie_results[0];
      result = {
        type: 'movie',
        tmdbId: m.id,
        imdbId,
        title: m.title,
        originalTitle: m.original_title,
        year: m.release_date ? parseInt(m.release_date.split('-')[0]) : null,
        cleanTitle: cleanTitle(m.title),
        cleanOriginalTitle: cleanTitle(m.original_title)
      };
    } else if (type === 'series' && data.tv_results && data.tv_results.length > 0) {
      const s = data.tv_results[0];
      result = {
        type: 'series',
        tmdbId: s.id,
        imdbId,
        title: s.name,
        originalTitle: s.original_name,
        year: s.first_air_date ? parseInt(s.first_air_date.split('-')[0]) : null,
        cleanTitle: cleanTitle(s.name),
        cleanOriginalTitle: cleanTitle(s.original_name)
      };
    }

    if (result) {
      cache.set(cacheKey, result);
      return result;
    }
  } catch (err) {
    console.error(`[TMDB] Error fetching details for ${imdbId}:`, err.message);
  }

  // Fallback to Cinemeta
  try {
    const cinemetaUrl = `https://v3-cinemeta.strem.io/meta/${type}/${imdbId}.json`;
    const res = await axios.get(cinemetaUrl, { timeout: 8000 });
    const meta = res.data?.meta;
    if (meta && meta.name) {
      const result = {
        type,
        tmdbId: meta.moviedb_id || null,
        imdbId,
        title: meta.name,
        originalTitle: meta.name,
        year: meta.year ? parseInt(meta.year) : null,
        cleanTitle: cleanTitle(meta.name),
        cleanOriginalTitle: cleanTitle(meta.name)
      };
      cache.set(cacheKey, result);
      return result;
    }
  } catch (err) {
    console.error(`[Cinemeta] Fallback error for ${imdbId}:`, err.message);
  }

  return null;
}

module.exports = {
  getMediaDetails,
  cleanTitle
};
