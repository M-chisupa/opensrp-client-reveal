package org.smartregister.reveal.dao;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;
import org.smartregister.dao.AbstractDao;
import org.smartregister.domain.Location;
import org.smartregister.domain.LocationProperty;
import org.smartregister.util.Cache;
import org.smartregister.util.PropertiesConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


public class StructureDao extends AbstractDao {

    public static StructureDao getInstance() {
        return new StructureDao();
    }

    protected static Cache<Map<String, List<Location>>> cache = new Cache<>();
    protected static String CACHED_STRUCTURES = "CACHED_STRUCTURES";

    public static Map<String, List<Location>> getStructuresByParent() {
        Map<String, List<Location>> result = new HashMap<>();

        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HHmm")
                .registerTypeAdapter(LocationProperty.class, new PropertiesConverter()).create();

        String sql = "select l.name , s.geojson from structure s inner join location l on s.parent_id = l._id";

        DataMap<Void> dataMap = cursor -> {
            String parent_id = getCursorValue(cursor, "name");
            if (StringUtils.isNotBlank(parent_id)) {
                List<Location> locations = result.get(parent_id);
                if (locations == null)
                    locations = new ArrayList<>();


                String geoJson = getCursorValue(cursor, "geojson");
                Location location = gson.fromJson(geoJson, Location.class);
                locations.add(location);

                result.put(parent_id, locations);
            }
            return null;
        };

        readData(sql, dataMap);

        return result;
    }

    public static Map<String, List<Location>> getCachedStructures() {
        return cache.get(CACHED_STRUCTURES, StructureDao::getStructuresByParent);
    }

    public static void resetLocationCache() {
        if (cache != null)
            cache.evict(CACHED_STRUCTURES);
    }

    /**
     * id a structure has a qr code return it
     *
     * @param qrCode
     * @return
     */
    public String getStructureQRCode(String qrCode) {
        String sql = "select structure_id from structure_eligibility where qr_code = '" + qrCode + "'";
        DataMap<String> dataMap = cursor -> getCursorValue(cursor, "structure_id");
        return readSingleValue(sql, dataMap);
    }

    public boolean structureHasQr(String structure_id) {
        String sql = "select qr_code from structure_eligibility where structure_id = '" + structure_id + "'";
        DataMap<String> dataMap = cursor -> getCursorValue(cursor, "qr_code");
        return StringUtils.isNotBlank(readSingleValue(sql, dataMap));
    }

    public String getFamilyBaseIDFromQRCode(String qrCode) {
        String sql = "select ec_family.base_entity_id from structure_eligibility " +
                "inner join  ec_family on ec_family.structure_id = structure_eligibility.structure_id " +
                "where qr_code = '" + qrCode + "'";
        DataMap<String> dataMap = cursor -> getCursorValue(cursor, "base_entity_id");
        return readSingleValue(sql, dataMap);
    }

    public String getStructureIDFromFamilyID(String familyBaseEntityID) {
        String sql = "select ec_family.structure_id from ec_family where ec_family.base_entity_id = '" + familyBaseEntityID + "'";
        DataMap<String> dataMap = cursor -> getCursorValue(cursor, "base_entity_id");
        return readSingleValue(sql, dataMap);
    }

    public String getFamilyIDFromStructureID(String structureID) {
        String sql = "select ec_family.base_entity_id from ec_family where ec_family.structure_id = '" + structureID + "'";
        DataMap<String> dataMap = cursor -> getCursorValue(cursor, "base_entity_id");
        return readSingleValue(sql, dataMap);
    }

    public void addDetails(String structureID, String qrCode) {
        String id = UUID.randomUUID().toString();
        String sql = String.format("insert into structure_eligibility(id , is_closed , structure_id , qr_code) values ('%s' , 0 , '%s' , '%s') ", id, structureID, qrCode);
        updateDB(sql);
    }

}
