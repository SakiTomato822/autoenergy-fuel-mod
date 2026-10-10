# Route map providers

The recording service and JSONL history remain independent of map providers.
`RouteMapController` is the display boundary (`showRoute`, `fitRoute`, `releaseMap`).
Current RouteCanvas implements a native, optional OSM raster renderer with the
same Web Mercator projection for both tiles and the WGS84 route overlay.

## Current OSM preview

- Disabled on every activity creation. Explicit consent enables network requests.
- Only visible tiles are requested, two simultaneous IO workers, bounded decoded
  memory (8 MiB) and disk cache (64 MiB maximum, trimmed to 48 MiB).
- Unique application User-Agent with repository contact URL; HTTPS; visible,
  clickable attribution. Cache retains tiles for at least seven days (or longer
  server expiry); conditional ETag/Last-Modified requests on expiry.
- No area download, offline map package, background prefetch, geocoding, routing,
  traffic, or navigation. Roads and existing place labels are baked into tiles.
- Map requests reveal the viewed area and IP to the tile operator. Full recorded
  routes are not sent as an API payload. Recording works with maps disabled.
- Public OSM servers have no SLA; connectivity and local POI coverage must be
  tested on the vehicle. Failed tiles leave route lines available with a warning.
- Plus/minus zoom around viewport centre, drag pan, 全程 fits route, tap selects
  recorded point. Synthetic emulator routes are explicitly labelled, not snapped
  to roads. Raster map has its original light appearance, no fake dark inversion.

Policy: https://operations.osmfoundation.org/policies/tiles/

## AMap reserved implementation

No AMap SDK or Key is bundled, and AMap is NOT currently functional. Replace the
map View/controller with an AMap-backed implementation behind the same contract;
leave RoutePoint, recording, private storage, and fuel sampling unchanged. Forward
SDK lifecycle to its MapView and expose the same point selection callback.

Stored coordinates are WGS84. Convert a display-only copy using the official SDK
GPS-to-GCJ02 converter for AMap; never rewrite history or apply GCJ02 offsets to
OSM. Obtain an Android Key bound to the actual package and signing certificate,
verify licensing, and complete SDK privacy consent before initialization.

Before wider distribution, re-evaluate the public tile service policy and choose
an appropriate OSM provider or licensed AMap plan. Current prototype is not an
offline navigation product or a guarantee of compliant commercial map deployment.
