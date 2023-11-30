-- Simple lua profile example that emits road features

-- setup archive metadata
planetiler.output.name = "Road Schema"
planetiler.output.description = "Simple"
planetiler.output.attribution =
'<a href="https://www.openstreetmap.org/copyright" target="_blank">&copy;OpenStreetMap contributors</a>'

-- tell planetiler where the tests are when you run `planetiler.jar validate roads_main.lua`
planetiler.examples = "roads.spec.yaml"

-- planetiler.process_feature is called by many threads so it can read from shared data structures
-- but not modify them
local highway_minzooms = {
  trunk = 5,
  primary = 7,
  secondary = 8,
  tertiary = 9,
  motorway_link = 9,
  trunk_link = 9,
  primary_link = 9,
  secondary_link = 9,
  tertiary_link = 9,
  unclassified = 11,
  residential = 11,
  living_street = 11,
  track = 12,
  service = 13
}

-- called by planetiler to map each input feature to output vector tile features
function planetiler.process_feature(source, features)
  local highway = source:get_tag("highway")
  if source:can_be_line() and highway and highway_minzooms[highway] then
    features:line("road")
        :set_min_zoom(highway_minzooms[highway])
        :set_attr("highway", highway)
  end
end

-- there are 2 ways to invoke planetiler: a main method (see roads_main.lua) and this method that
-- sets up planetiler statically
-- TODO not sure which is better?
local area = planetiler.args:get_string("area", "geofabrik area to download", "germany")
planetiler.add_source('osm', {
  type = 'osm',
  path = { 'data', 'sources', area .. '.osm.pbf' },
  url = 'geofabrik:' .. area
})
planetiler.output.path = 'roads.pmtiles'
