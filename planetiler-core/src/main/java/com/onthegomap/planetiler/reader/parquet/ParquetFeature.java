package com.onthegomap.planetiler.reader.parquet;

import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.Struct;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Lineal;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.Puntal;

/**
 * A single record read from a geoparquet file.
 */
public class ParquetFeature extends SourceFeature {

  private final GeometryReader geometryParser;
  private final Path filename;
  private Geometry latLon;
  private Geometry world;
  private Struct struct = null;

  ParquetFeature(String source, String sourceLayer, Path filename, long id, GeometryReader geometryParser,
    Map<String, Object> tags) {
    super(tags, source, sourceLayer, List.of(), id);
    this.geometryParser = geometryParser;
    this.filename = filename;
  }

  public Path getFilename() {
    return filename;
  }

  @Override
  public Geometry latLonGeometry() throws GeometryException {
    return latLon == null ? latLon = geometryParser.readPrimaryGeometry(this) : latLon;
  }

  @Override
  public Geometry worldGeometry() throws GeometryException {
    return world != null ? world :
      (world = GeoUtils.sortPolygonsByAreaDescending(GeoUtils.latLonToWorldCoords(latLonGeometry())));
  }

  @Override
  public boolean isPoint() {
    try {
      return latLonGeometry() instanceof Puntal;
    } catch (GeometryException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean canBePolygon() {
    try {
      return latLonGeometry() instanceof Polygonal;
    } catch (GeometryException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean canBeLine() {
    try {
      return latLonGeometry() instanceof Lineal;
    } catch (GeometryException e) {
      throw new IllegalStateException(e);
    }
  }

  private Struct cachedStruct() {
    return struct != null ? struct : (struct = Struct.of(tags()));
  }

  @Override
  public Struct getStruct(String key) {
    return cachedStruct().get(key);
  }

  @Override
  public Struct getStruct(Object key, Object... others) {
    return cachedStruct().get(key, others);
  }

  @Override
  public Object getTag(String key) {
    var value = tags().get(key);
    if (value == null) {
      String[] parts = key.split("\\.");
      if (parts.length > 1) {
        Object[] rest = Arrays.copyOfRange(parts, 1, parts.length);
        value = getStruct(parts[0], rest).rawValue();
      }
    }
    return value;
  }

  @Override
  public Object getTag(String key, Object defaultValue) {
    var value = getTag(key);
    if (value == null) {
      value = defaultValue;
    }
    return value;
  }

  @Override
  public boolean hasTag(String key) {
    return super.hasTag(key) || getTag(key) != null;
  }

  @Override
  public String toString() {
    return tags().toString();
  }
}
