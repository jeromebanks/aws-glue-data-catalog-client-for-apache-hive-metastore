package com.amazonaws.glue.catalog.converters;

import com.amazonaws.services.glue.model.ErrorDetail;
import static com.amazonaws.glue.catalog.converters.S3ToS3a.S3LocationToS3a;

import org.apache.hadoop.hive.metastore.api.AlreadyExistsException;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Function;
import org.apache.hadoop.hive.metastore.api.FunctionType;
import org.apache.hadoop.hive.metastore.api.Index;
import org.apache.hadoop.hive.metastore.api.InvalidObjectException;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.Order;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.PrincipalType;
import org.apache.hadoop.hive.metastore.api.ResourceType;
import org.apache.hadoop.hive.metastore.api.ResourceUri;
import org.apache.hadoop.hive.metastore.api.SerDeInfo;
import org.apache.hadoop.hive.metastore.api.SkewedInfo;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.api.TableMeta;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.amazonaws.glue.catalog.converters.ConverterUtils.INDEX_DEFERRED_REBUILD;
import static com.amazonaws.glue.catalog.converters.ConverterUtils.INDEX_TABLE_NAME;
import static com.amazonaws.glue.catalog.converters.ConverterUtils.INDEX_DB_NAME;
import static com.amazonaws.glue.catalog.converters.ConverterUtils.INDEX_HANDLER_CLASS;
import static com.amazonaws.glue.catalog.converters.ConverterUtils.INDEX_ORIGIN_TABLE_NAME;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

public class CatalogToHiveConverter {

  private static final Logger logger = Logger.getLogger(CatalogToHiveConverter.class);

  private static final ImmutableMap<String, HiveException> EXCEPTION_MAP = ImmutableMap.<String, HiveException>builder()
      .put("AlreadyExistsException", new HiveException() {
        public TException get(String msg) {
          return new AlreadyExistsException(msg);
        }
      })
      .put("InvalidInputException", new HiveException() {
        public TException get(String msg) {
          return new InvalidObjectException(msg);
        }
      })
      .put("InternalServiceException", new HiveException() {
        public TException get(String msg) {
          return new MetaException(msg);
        }
      })
      .put("ResourceNumberLimitExceededException", new HiveException() {
        public TException get(String msg) {
          return new MetaException(msg);
        }
      })
      .put("OperationTimeoutException", new HiveException() {
        public TException get(String msg) {
          return new MetaException(msg);
        }
      })
      .put("EntityNotFoundException", new HiveException() {
        public TException get(String msg) {
          return new NoSuchObjectException(msg);
        }
      })
      .build();

  interface HiveException {
    TException get(String msg);
  }

  public static TException wrapInHiveException(Throwable e) {
    return getHiveException(e.getClass().getSimpleName(), e.getMessage());
  }

  public static TException errorDetailToHiveException(ErrorDetail errorDetail) {
    return getHiveException(errorDetail.getErrorCode(), errorDetail.getErrorMessage());
  }

  private static TException getHiveException(String errorName, String errorMsg) {
    if (EXCEPTION_MAP.containsKey(errorName)) {
      return EXCEPTION_MAP.get(errorName).get(errorMsg);
    } else {
      logger.warn("Hive Exception type not found for " + errorName);
      return new MetaException(errorMsg);
    }
  }

  public static Database convertDatabase(com.amazonaws.services.glue.model.Database catalogDatabase) {
    Database hiveDatabase = new Database();
    hiveDatabase.setName(catalogDatabase.getName());
    hiveDatabase.setDescription(catalogDatabase.getDescription());
    String location = S3LocationToS3a(catalogDatabase.getLocationUri());
    hiveDatabase.setLocationUri(location == null ? "" : location);
    hiveDatabase.setParameters(firstNonNull(catalogDatabase.getParameters(), Maps.<String, String>newHashMap()));
    return hiveDatabase;
  }

  public static FieldSchema convertFieldSchema(com.amazonaws.services.glue.model.Column catalogFieldSchema) {
    FieldSchema hiveFieldSchema = new FieldSchema();
    hiveFieldSchema.setType(catalogFieldSchema.getType());
    hiveFieldSchema.setName(catalogFieldSchema.getName());
    hiveFieldSchema.setComment(catalogFieldSchema.getComment());

    return hiveFieldSchema;
  }

  public static List<FieldSchema> convertFieldSchemaList(List<com.amazonaws.services.glue.model.Column> catalogFieldSchemaList) {
    List<FieldSchema> hiveFieldSchemaList = new ArrayList<>();
    if (catalogFieldSchemaList == null) {
      return hiveFieldSchemaList;
    }
    for (com.amazonaws.services.glue.model.Column catalogFieldSchema : catalogFieldSchemaList){
      hiveFieldSchemaList.add(convertFieldSchema(catalogFieldSchema));
    }

    return hiveFieldSchemaList;
  }

  public static Table convertTable(com.amazonaws.services.glue.model.Table catalogTable, String dbname) {
    Table hiveTable = new Table();
    hiveTable.setDbName(dbname);
    hiveTable.setTableName(catalogTable.getName());
    Date createTime = catalogTable.getCreateTime();
    hiveTable.setCreateTime(createTime == null ? 0 : (int) (createTime.getTime() / 1000));
    hiveTable.setOwner(catalogTable.getOwner());
    Date lastAccessedTime = catalogTable.getLastAccessTime();
    hiveTable.setLastAccessTime(lastAccessedTime == null ? 0 : (int) (lastAccessedTime.getTime() / 1000));
    hiveTable.setRetention(catalogTable.getRetention());
    hiveTable.setSd(convertStorageDescriptor(catalogTable.getStorageDescriptor()));
    hiveTable.setPartitionKeys(convertFieldSchemaList(catalogTable.getPartitionKeys()));
    // Hive may throw a NPE during dropTable if the parameter map is null.
    Map<String, String> parameterMap = catalogTable.getParameters();
    if (parameterMap == null) {
      parameterMap = Maps.newHashMap();
    }
    hiveTable.setParameters(parameterMap);
    hiveTable.setViewOriginalText(catalogTable.getViewOriginalText());
    hiveTable.setViewExpandedText(catalogTable.getViewExpandedText());
    hiveTable.setTableType(catalogTable.getTableType());

    return hiveTable;
  }

  public static TableMeta convertTableMeta(com.amazonaws.services.glue.model.Table catalogTable, String dbName) {
    TableMeta tableMeta = new TableMeta();
    tableMeta.setDbName(dbName);
    tableMeta.setTableName(catalogTable.getName());
    tableMeta.setTableType(catalogTable.getTableType());
    if (catalogTable.getParameters().containsKey("comment")) {
      tableMeta.setComments(catalogTable.getParameters().get("comment"));
    }
    return tableMeta;
  }

  public static StorageDescriptor convertStorageDescriptor(com.amazonaws.services.glue.model.StorageDescriptor catalogSd) {
    StorageDescriptor hiveSd = new StorageDescriptor();
    hiveSd.setCols(convertFieldSchemaList(catalogSd.getColumns()));
    hiveSd.setLocation(S3LocationToS3a(catalogSd.getLocation()));
    hiveSd.setInputFormat(catalogSd.getInputFormat());
    hiveSd.setOutputFormat(catalogSd.getOutputFormat());
    hiveSd.setCompressed(catalogSd.getCompressed());
    hiveSd.setNumBuckets(catalogSd.getNumberOfBuckets());
    hiveSd.setSerdeInfo(convertSerDeInfo(catalogSd.getSerdeInfo()));
    hiveSd.setBucketCols(firstNonNull(catalogSd.getBucketColumns(), Lists.<String>newArrayList()));
    hiveSd.setSortCols(convertOrderList(catalogSd.getSortColumns()));
    hiveSd.setParameters(firstNonNull(catalogSd.getParameters(), Maps.<String, String>newHashMap()));
    hiveSd.setSkewedInfo(convertSkewedInfo(catalogSd.getSkewedInfo()));
    hiveSd.setStoredAsSubDirectories(catalogSd.getStoredAsSubDirectories());

    return hiveSd;
  }

  public static Order convertOrder(com.amazonaws.services.glue.model.Order catalogOrder) {
    Order hiveOrder = new Order();
    hiveOrder.setCol(catalogOrder.getColumn());
    hiveOrder.setOrder(catalogOrder.getSortOrder());

    return hiveOrder;
  }

  public static List<Order> convertOrderList(List<com.amazonaws.services.glue.model.Order> catalogOrderList) {
    List<Order> hiveOrderList = new ArrayList<>();
    if (catalogOrderList == null) {
      return hiveOrderList;
    }
    for (com.amazonaws.services.glue.model.Order catalogOrder : catalogOrderList){
      hiveOrderList.add(convertOrder(catalogOrder));
    }

    return hiveOrderList;
  }

  public static SerDeInfo convertSerDeInfo(com.amazonaws.services.glue.model.SerDeInfo catalogSerDeInfo){
    SerDeInfo hiveSerDeInfo = new SerDeInfo();
    hiveSerDeInfo.setName(catalogSerDeInfo.getName());
    hiveSerDeInfo.setParameters(firstNonNull(catalogSerDeInfo.getParameters(), Maps.<String, String>newHashMap()));
    hiveSerDeInfo.setSerializationLib(catalogSerDeInfo.getSerializationLibrary());

    return hiveSerDeInfo;
  }

  public static SkewedInfo convertSkewedInfo(com.amazonaws.services.glue.model.SkewedInfo catalogSkewedInfo) {
    if (catalogSkewedInfo == null) {
      return null;
    }
    
    SkewedInfo hiveSkewedInfo = new SkewedInfo();
    hiveSkewedInfo.setSkewedColNames(firstNonNull(catalogSkewedInfo.getSkewedColumnNames(), Lists.<String>newArrayList()));
    hiveSkewedInfo.setSkewedColValues(convertSkewedValue(catalogSkewedInfo.getSkewedColumnValues()));
    hiveSkewedInfo.setSkewedColValueLocationMaps(convertSkewedMap(catalogSkewedInfo.getSkewedColumnValueLocationMaps()));
    return hiveSkewedInfo;
  }

  public static Index convertTableObjectToIndex(com.amazonaws.services.glue.model.Table catalogTable) {
    Index hiveIndex = new Index();
    Map<String, String> parameters = catalogTable.getParameters();
    hiveIndex.setIndexName(catalogTable.getName());
    hiveIndex.setCreateTime((int) (catalogTable.getCreateTime().getTime() / 1000));
    hiveIndex.setLastAccessTime((int) (catalogTable.getLastAccessTime().getTime() / 1000));
    hiveIndex.setSd(convertStorageDescriptor(catalogTable.getStorageDescriptor()));
    hiveIndex.setParameters(catalogTable.getParameters());

    hiveIndex.setDeferredRebuild(parameters.get(INDEX_DEFERRED_REBUILD).equals("TRUE"));
    hiveIndex.setIndexHandlerClass(parameters.get(INDEX_HANDLER_CLASS));
    hiveIndex.setDbName(parameters.get(INDEX_DB_NAME));
    hiveIndex.setOrigTableName(parameters.get(INDEX_ORIGIN_TABLE_NAME));
    hiveIndex.setIndexTableName(parameters.get(INDEX_TABLE_NAME));

    return hiveIndex;
  }

  public static Partition convertPartition(com.amazonaws.services.glue.model.Partition src) {
	  Partition tgt = new Partition();
	  Date createTime = src.getCreationTime();
	  if (createTime != null) {
		  tgt.setCreateTime((int) (createTime.getTime() / 1000)); 
		  tgt.setCreateTimeIsSet(true);
	  } else {
		  tgt.setCreateTimeIsSet(false);
	  }
	  String dbName = src.getDatabaseName();
	  if (dbName != null) {
		  tgt.setDbName(dbName);
		  tgt.setDbNameIsSet(true);
	  } else {
		  tgt.setDbNameIsSet(false);
	  }
	  Date lastAccessTime = src.getLastAccessTime();
	  if (lastAccessTime != null) {
		  tgt.setLastAccessTime((int) (lastAccessTime.getTime() / 1000));
		  tgt.setLastAccessTimeIsSet(true);
	  } else {
		  tgt.setLastAccessTimeIsSet(false);
	  }
	  Map<String, String> params = src.getParameters();
	  
	  // A null parameter map causes Hive to throw a NPE
	  // so ensure we do not return a Partition object with a null parameter map.
	  if (params == null) {
	    params = Maps.newHashMap();
	  }
	  
	  tgt.setParameters(params);
	  tgt.setParametersIsSet(true);
	  
	  String tableName = src.getTableName();
	  if (tableName != null) {
		  tgt.setTableName(tableName);
		  tgt.setTableNameIsSet(true);
	  } else {
		  tgt.setTableNameIsSet(false);
	  }
	  
	  List<String> values = src.getValues();
	  if (values != null) {
		  tgt.setValues(values);
		  tgt.setValuesIsSet(true);
	  } else {
		  tgt.setValuesIsSet(false);
	  }
	  
	  com.amazonaws.services.glue.model.StorageDescriptor sd = src.getStorageDescriptor();
	  if (sd != null) {
		  StorageDescriptor hiveSd = convertStorageDescriptor(sd);
		  tgt.setSd(hiveSd);
		  tgt.setSdIsSet(true);
	  } else {
		  tgt.setSdIsSet(false);
	  }
	  
	  return tgt;
  }

  public static List<Partition> convertPartitions(List<com.amazonaws.services.glue.model.Partition> src) {
    if (src == null) {
      return null;
    }

    List<Partition> target = Lists.newArrayList();
    for (com.amazonaws.services.glue.model.Partition partition : src) {
      target.add(convertPartition(partition));
    }
    return target;
  }

  public static List<String> convertStringToList(final String s) {
    if (s == null) {
      return null;
    }
    List<String> listString = new ArrayList<>();
    for (int i = 0; i < s.length();) {
      StringBuilder length = new StringBuilder();
      for (int j = i; j < s.length(); j++){
        if (s.charAt(j) != '$') {
          length.append(s.charAt(j));
        } else {
          int lengthOfString = Integer.valueOf(length.toString());
          listString.add(s.substring(j + 1, j + 1 + lengthOfString));
          i = j + 1 + lengthOfString;
          break;
        }
      }
    }
    return listString;
  }

  @Nonnull
  public static Map<List<String>, String> convertSkewedMap(final @Nullable Map<String, String> catalogSkewedMap) {
    Map<List<String>, String> skewedMap = new HashMap<>();
    if (catalogSkewedMap == null){
      return skewedMap;
    }

    for (String coralKey : catalogSkewedMap.keySet()) {
      skewedMap.put(convertStringToList(coralKey), catalogSkewedMap.get(coralKey));
    }
    return skewedMap;
  }

  @Nonnull
  public static List<List<String>> convertSkewedValue(final @Nullable List<String> catalogSkewedValue) {
    List<List<String>> skewedValues = new ArrayList<>();
    if (catalogSkewedValue == null){
      return skewedValues;
    }

    for (String skewValue : catalogSkewedValue) {
      skewedValues.add(convertStringToList(skewValue));
    }
    return skewedValues;
  }
  
  public static PrincipalType convertPrincipalType(com.amazonaws.services.glue.model.PrincipalType catalogPrincipalType) {
    if(catalogPrincipalType == null) {
      return null;
    }
    
    if(catalogPrincipalType == com.amazonaws.services.glue.model.PrincipalType.GROUP) {
      return PrincipalType.GROUP;
    } else if(catalogPrincipalType == com.amazonaws.services.glue.model.PrincipalType.USER) {
      return PrincipalType.USER;
    } else if(catalogPrincipalType == com.amazonaws.services.glue.model.PrincipalType.ROLE) {
      return PrincipalType.ROLE;
    }
    throw new RuntimeException("Unknown principal type:" + catalogPrincipalType.name());
  }

  public static Function convertFunction(final String dbName,
                                         final com.amazonaws.services.glue.model.UserDefinedFunction catalogFunction) {
    if (catalogFunction ==  null) {
      return null;
    }
    Function hiveFunction = new Function();
    hiveFunction.setClassName(catalogFunction.getClassName());
    hiveFunction.setCreateTime((int)(catalogFunction.getCreateTime().getTime() / 1000));
    hiveFunction.setDbName(dbName);
    hiveFunction.setFunctionName(catalogFunction.getFunctionName());
    hiveFunction.setFunctionType(FunctionType.JAVA);
    hiveFunction.setOwnerName(catalogFunction.getOwnerName());
    hiveFunction.setOwnerType(convertPrincipalType(com.amazonaws.services.glue.model.PrincipalType.fromValue(catalogFunction.getOwnerType())));
    hiveFunction.setResourceUris(convertResourceUriList(catalogFunction.getResourceUris()));
    return hiveFunction;
  }

  public static List<ResourceUri> convertResourceUriList(
          final List<com.amazonaws.services.glue.model.ResourceUri> catalogResourceUriList) {
    if (catalogResourceUriList == null) {
      return null;
    }
    List<ResourceUri> hiveResourceUriList = new ArrayList<>();
    for (com.amazonaws.services.glue.model.ResourceUri catalogResourceUri : catalogResourceUriList) {
      ResourceUri hiveResourceUri = new ResourceUri();
      hiveResourceUri.setUri(catalogResourceUri.getUri());
      if (catalogResourceUri.getResourceType() != null) {
        hiveResourceUri.setResourceType(ResourceType.valueOf(catalogResourceUri.getResourceType()));
      }
      hiveResourceUriList.add(hiveResourceUri);
    }

    return hiveResourceUriList;
  }

}