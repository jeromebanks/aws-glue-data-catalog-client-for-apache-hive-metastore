package com.amazonaws.glue.catalog.converters;

import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Function;
import org.apache.hadoop.hive.metastore.api.Index;
import org.apache.hadoop.hive.metastore.api.Order;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.ResourceUri;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
import org.apache.hadoop.hive.metastore.api.SkewedInfo;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.Table;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.amazonaws.glue.catalog.converters.S3ToS3a.S3aLocationToS3;
import static com.amazonaws.glue.catalog.converters.ConverterUtils.INDEX_DB_NAME;
import static com.amazonaws.glue.catalog.converters.ConverterUtils.INDEX_DEFERRED_REBUILD;
import static com.amazonaws.glue.catalog.converters.ConverterUtils.INDEX_HANDLER_CLASS;
import static com.amazonaws.glue.catalog.converters.ConverterUtils.INDEX_ORIGIN_TABLE_NAME;
import static com.amazonaws.glue.catalog.converters.ConverterUtils.INDEX_TABLE_NAME;

public class HiveToCatalogConverter {

  public static com.amazonaws.services.glue.model.Database convertDatabase(Database hiveDatabase) {
    com.amazonaws.services.glue.model.Database catalogDatabase = new com.amazonaws.services.glue.model.Database();
    catalogDatabase.setName(hiveDatabase.getName());
    catalogDatabase.setDescription(hiveDatabase.getDescription());
    catalogDatabase.setLocationUri(S3aLocationToS3(hiveDatabase.getLocationUri()));
    catalogDatabase.setParameters(hiveDatabase.getParameters());
    return catalogDatabase;
  }

  public static com.amazonaws.services.glue.model.Table convertTable(
          Table hiveTable) {
    com.amazonaws.services.glue.model.Table catalogTable = new com.amazonaws.services.glue.model.Table();
    catalogTable.setRetention(hiveTable.getRetention());
    catalogTable.setPartitionKeys(convertFieldSchemaList(hiveTable.getPartitionKeys()));
    catalogTable.setTableType(hiveTable.getTableType());
    catalogTable.setName(hiveTable.getTableName());
    catalogTable.setOwner(hiveTable.getOwner());
    catalogTable.setCreateTime(new Date((long) hiveTable.getCreateTime() * 1000));
    catalogTable.setLastAccessTime(new Date((long) hiveTable.getLastAccessTime() * 1000));
    catalogTable.setStorageDescriptor(convertStorageDescriptor(hiveTable.getSd()));
    catalogTable.setParameters(hiveTable.getParameters());
    catalogTable.setViewExpandedText(hiveTable.getViewExpandedText());
    catalogTable.setViewOriginalText(hiveTable.getViewOriginalText());

    return catalogTable;
  }

  public static com.amazonaws.services.glue.model.StorageDescriptor convertStorageDescriptor(
          StorageDescriptor hiveSd) {
    com.amazonaws.services.glue.model.StorageDescriptor catalogSd =
            new com.amazonaws.services.glue.model.StorageDescriptor();
    catalogSd.setNumberOfBuckets(hiveSd.getNumBuckets());
    catalogSd.setCompressed(hiveSd.isCompressed());
    catalogSd.setParameters(hiveSd.getParameters());
    catalogSd.setBucketColumns(hiveSd.getBucketCols());
    catalogSd.setColumns(convertFieldSchemaList(hiveSd.getCols()));
    catalogSd.setInputFormat(hiveSd.getInputFormat());
    catalogSd.setLocation(S3aLocationToS3(hiveSd.getLocation()));
    catalogSd.setOutputFormat(hiveSd.getOutputFormat());
    catalogSd.setSerdeInfo(convertSerDeInfo(hiveSd.getSerdeInfo()));
    catalogSd.setSkewedInfo(convertSkewedInfo(hiveSd.getSkewedInfo()));
    catalogSd.setSortColumns(convertOrderList(hiveSd.getSortCols()));
    catalogSd.setStoredAsSubDirectories(hiveSd.isStoredAsSubDirectories());

    return catalogSd;
  }

  public static com.amazonaws.services.glue.model.Column convertFieldSchema(
          FieldSchema hiveFieldSchema) {
    com.amazonaws.services.glue.model.Column catalogFieldSchema =
            new com.amazonaws.services.glue.model.Column();
    catalogFieldSchema.setComment(hiveFieldSchema.getComment());
    catalogFieldSchema.setName(hiveFieldSchema.getName());
    catalogFieldSchema.setType(hiveFieldSchema.getType());

    return catalogFieldSchema;
  }

  public static List<com.amazonaws.services.glue.model.Column> convertFieldSchemaList(
          List<FieldSchema> hiveFieldSchemaList) {
    List<com.amazonaws.services.glue.model.Column> catalogFieldSchemaList =
            new ArrayList<com.amazonaws.services.glue.model.Column>();
    for (FieldSchema hiveFs : hiveFieldSchemaList){
      catalogFieldSchemaList.add(convertFieldSchema(hiveFs));
    }

    return catalogFieldSchemaList;
  }

  public static com.amazonaws.services.glue.model.SerDeInfo convertSerDeInfo(
          SerDeInfo hiveSerDeInfo) {
    com.amazonaws.services.glue.model.SerDeInfo catalogSerDeInfo = new com.amazonaws.services.glue.model.SerDeInfo();
    catalogSerDeInfo.setName(hiveSerDeInfo.getName());
    catalogSerDeInfo.setParameters(hiveSerDeInfo.getParameters());
    catalogSerDeInfo.setSerializationLibrary(hiveSerDeInfo.getSerializationLib());

    return catalogSerDeInfo;
  }

  public static com.amazonaws.services.glue.model.SkewedInfo convertSkewedInfo(SkewedInfo hiveSkewedInfo) {
    if (hiveSkewedInfo == null) 
      return null;
    com.amazonaws.services.glue.model.SkewedInfo catalogSkewedInfo = new com.amazonaws.services.glue.model.SkewedInfo()
              .withSkewedColumnNames(hiveSkewedInfo.getSkewedColNames())
              .withSkewedColumnValues(convertSkewedValue(hiveSkewedInfo.getSkewedColValues()))
              .withSkewedColumnValueLocationMaps(convertSkewedMap(hiveSkewedInfo.getSkewedColValueLocationMaps()));
    return catalogSkewedInfo;
  }

  public static com.amazonaws.services.glue.model.Order convertOrder(Order hiveOrder) {
    com.amazonaws.services.glue.model.Order order = new com.amazonaws.services.glue.model.Order();
    order.setColumn(hiveOrder.getCol());
    order.setSortOrder(hiveOrder.getOrder());

    return order;
  }

  public static List<com.amazonaws.services.glue.model.Order> convertOrderList(List<Order> hiveOrderList) {
    if (hiveOrderList == null) {
      return null;
    }
    List<com.amazonaws.services.glue.model.Order> catalogOrderList = new ArrayList<>();
    for (Order hiveOrder : hiveOrderList) {
      catalogOrderList.add(convertOrder(hiveOrder));
    }

    return catalogOrderList;
  }

  public static com.amazonaws.services.glue.model.Table convertIndexToTableObject(Index hiveIndex) {
    // convert index object to a table object
    com.amazonaws.services.glue.model.Table catalogIndexTableObject = new com.amazonaws.services.glue.model.Table();
    catalogIndexTableObject.setName(hiveIndex.getIndexName());
    catalogIndexTableObject.setCreateTime(new Date((long) (hiveIndex.getCreateTime()) * 1000));
    catalogIndexTableObject.setLastAccessTime(new Date((long) (hiveIndex.getLastAccessTime()) * 1000));
    catalogIndexTableObject.setStorageDescriptor(convertStorageDescriptor(hiveIndex.getSd()));
    catalogIndexTableObject.setParameters(hiveIndex.getParameters());

    // store rest of fields in index to paramter map
    catalogIndexTableObject.getParameters().put(INDEX_DEFERRED_REBUILD, hiveIndex.isDeferredRebuild() ? "TRUE": "FALSE");
    catalogIndexTableObject.getParameters().put(INDEX_TABLE_NAME, hiveIndex.getIndexTableName());
    catalogIndexTableObject.getParameters().put(INDEX_HANDLER_CLASS, hiveIndex.getIndexHandlerClass());
    catalogIndexTableObject.getParameters().put(INDEX_DB_NAME, hiveIndex.getDbName());
    catalogIndexTableObject.getParameters().put(INDEX_ORIGIN_TABLE_NAME, hiveIndex.getOrigTableName());

    return catalogIndexTableObject;
  }
  
  public static com.amazonaws.services.glue.model.Partition convertPartition(Partition src) {
    com.amazonaws.services.glue.model.Partition tgt = new com.amazonaws.services.glue.model.Partition();
    
    tgt.setDatabaseName(src.getDbName());
    tgt.setTableName(src.getTableName());
    tgt.setCreationTime(new Date((long) src.getCreateTime() * 1000)); 
    tgt.setLastAccessTime(new Date((long) src.getLastAccessTime() * 1000)); 
    tgt.setParameters(src.getParameters());
    tgt.setStorageDescriptor(convertStorageDescriptor(src.getSd()));
    tgt.setValues(src.getValues());
    
    return tgt;
  }

  public static String convertListToString(final List<String> list) {
    if (list == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < list.size(); i++) {
      String currentString = list.get(i);
      sb.append(currentString.length() + "$" + currentString);
    }

    return sb.toString();
  }

  public static Map<String, String> convertSkewedMap(final Map<List<String>, String> coreSkewedMap){
    if (coreSkewedMap == null){
      return null;
    }
    Map<String, String> catalogSkewedMap = new HashMap<>();
    for (List<String> coreKey : coreSkewedMap.keySet()) {
      catalogSkewedMap.put(convertListToString(coreKey), coreSkewedMap.get(coreKey));
    }
    return catalogSkewedMap;
  }

  public static List<String> convertSkewedValue(final List<List<String>> coreSkewedValue) {
    if (coreSkewedValue == null) {
      return null;
    }
    List<String> catalogSkewedValue = new ArrayList<>();
    for (int i = 0; i < coreSkewedValue.size(); i++) {
      catalogSkewedValue.add(convertListToString(coreSkewedValue.get(i)));
    }

    return catalogSkewedValue;
  }

  public static com.amazonaws.services.glue.model.UserDefinedFunction convertFunction(final Function hiveFunction) {
    if (hiveFunction == null ){
      return null;
    }
    com.amazonaws.services.glue.model.UserDefinedFunction catalogFunction = new com.amazonaws.services.glue.model.UserDefinedFunction();
    catalogFunction.setClassName(hiveFunction.getClassName());
    catalogFunction.setFunctionName(hiveFunction.getFunctionName());
    catalogFunction.setCreateTime(new Date((long) (hiveFunction.getCreateTime()) * 1000));
    catalogFunction.setOwnerName(hiveFunction.getOwnerName());
    if(hiveFunction.getOwnerType() != null) {
      catalogFunction.setOwnerType(hiveFunction.getOwnerType().name());
    }
    catalogFunction.setResourceUris(covertResourceUriList(hiveFunction.getResourceUris()));
    return catalogFunction;
  }

  public static List<com.amazonaws.services.glue.model.ResourceUri> covertResourceUriList(
          final List<ResourceUri> hiveResourceUriList) {
    if (hiveResourceUriList == null) {
      return null;
    }
    List<com.amazonaws.services.glue.model.ResourceUri> catalogResourceUriList = new ArrayList<>();
    for (ResourceUri hiveResourceUri : hiveResourceUriList) {
      com.amazonaws.services.glue.model.ResourceUri catalogResourceUri = new com.amazonaws.services.glue.model.ResourceUri();
      catalogResourceUri.setUri(hiveResourceUri.getUri());
      if (hiveResourceUri.getResourceType() != null) {
        catalogResourceUri.setResourceType(hiveResourceUri.getResourceType().name());
      }
      catalogResourceUriList.add(catalogResourceUri);
    }
    return catalogResourceUriList;
  }

}