const { addonBuilder } = require('stremio-addon-sdk');
const { getMediaDetails } = require('./services/tmdb');
const { getMovieStreams, getSeriesStreams } = require('./services/hackstore');

const manifest = {
  id: 'org.raava.streams',
  version: '1.0.0',
  name: 'RAAVA Streams',
  description: 'Películas y Series en Español Latino Dual (4K HDR / 1080p). Streams P2P ultra rápidos sin anuncios.',
  logo: 'https://i.ibb.co/vzG7P3J/raava-logo.png',
  background: 'https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?auto=format&fit=crop&w=1920&q=80',
  resources: ['stream'],
  types: ['movie', 'series'],
  idPrefixes: ['tt'],
  catalogs: [],
  behaviorHints: {
    configurable: false
  }
};

const builder = new addonBuilder(manifest);

builder.defineStreamHandler(async ({ type, id }) => {
  console.log(`[RAAVA Request] Type: ${type}, ID: ${id}`);

  try {
    let imdbId = id;
    let season = null;
    let episode = null;

    if (id.includes(':')) {
      const parts = id.split(':');
      imdbId = parts[0];
      season = parts[1];
      episode = parts[2];
    }

    const media = await getMediaDetails(type, imdbId);
    if (!media) {
      console.log(`[RAAVA] Could not resolve media details for ID: ${id}`);
      return { streams: [] };
    }

    let streams = [];
    if (type === 'movie') {
      streams = await getMovieStreams(media);
    } else if (type === 'series' && season && episode) {
      streams = await getSeriesStreams(media, season, episode);
    }

    console.log(`[RAAVA] Returning ${streams.length} stream(s) for "${media.title}"`);
    return { streams };
  } catch (error) {
    console.error(`[RAAVA Error] Failed to handle stream for ${id}:`, error);
    return { streams: [] };
  }
});

module.exports = builder.getInterface();
