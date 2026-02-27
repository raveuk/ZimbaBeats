/**
 * ZimbaBeats Audio Stream Worker
 * Cloudflare Worker that proxies requests to Piped instances
 * with automatic failover for reliability.
 *
 * Deploy: wrangler deploy
 */

// List of Piped instances to try (regularly updated)
const PIPED_INSTANCES = [
  'https://pipedapi.kavin.rocks',
  'https://pipedapi.adminforge.de',
  'https://api.piped.yt',
  'https://pipedapi.in.projectsegfau.lt',
  'https://pipedapi.osphost.fi',
  'https://api.piped.privacydev.net',
  'https://pipedapi.darkness.services',
  'https://pipedapi.drgns.space',
];

// CORS headers
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
};

export default {
  async fetch(request, env, ctx) {
    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    // Health check
    if (path === '/health' || path === '/') {
      return new Response(JSON.stringify({
        status: 'ok',
        version: '1.0.0',
        instances: PIPED_INSTANCES.length,
      }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // Get audio stream
    if (path === '/stream' || path === '/api/stream') {
      const videoId = url.searchParams.get('videoId');

      if (!videoId) {
        return new Response(JSON.stringify({
          success: false,
          error: 'Missing videoId parameter',
        }), {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      return await getAudioStream(videoId);
    }

    return new Response('Not Found', { status: 404, headers: corsHeaders });
  },
};

async function getAudioStream(videoId) {
  console.log(`Getting audio stream for: ${videoId}`);

  // Shuffle instances for load balancing
  const shuffledInstances = [...PIPED_INSTANCES].sort(() => Math.random() - 0.5);

  let lastError = null;

  for (const instance of shuffledInstances) {
    try {
      console.log(`Trying instance: ${instance}`);

      const response = await fetch(`${instance}/streams/${videoId}`, {
        headers: {
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        },
      });

      if (!response.ok) {
        console.log(`Instance ${instance} returned ${response.status}`);
        continue;
      }

      const data = await response.json();

      // Check for error in response
      if (data.error) {
        console.log(`Instance ${instance} error: ${data.error}`);
        continue;
      }

      // Find best audio stream
      const audioStreams = data.audioStreams || [];
      if (audioStreams.length === 0) {
        console.log(`Instance ${instance} has no audio streams`);
        continue;
      }

      // Sort by bitrate (highest first)
      audioStreams.sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0));
      const bestAudio = audioStreams[0];

      console.log(`Success from ${instance}: ${data.title}`);

      return new Response(JSON.stringify({
        success: true,
        audioUrl: bestAudio.url,
        mimeType: bestAudio.mimeType || 'audio/mp4',
        bitrate: bestAudio.bitrate,
        title: data.title || 'Unknown Title',
        author: data.uploader || 'Unknown Artist',
        duration: data.duration || 0,
        thumbnail: data.thumbnailUrl || `https://i.ytimg.com/vi/${videoId}/mqdefault.jpg`,
        source: instance,
      }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });

    } catch (error) {
      console.log(`Instance ${instance} failed: ${error.message}`);
      lastError = error;
      continue;
    }
  }

  // All instances failed
  return new Response(JSON.stringify({
    success: false,
    error: lastError?.message || 'All Piped instances failed',
  }), {
    status: 503,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
  });
}
