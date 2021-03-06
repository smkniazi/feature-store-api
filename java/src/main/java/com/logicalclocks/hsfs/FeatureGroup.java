/*
 * Copyright (c) 2020 Logical Clocks AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.logicalclocks.hsfs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.logicalclocks.hsfs.engine.FeatureGroupEngine;
import com.logicalclocks.hsfs.metadata.FeatureGroupBase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureGroup extends FeatureGroupBase {

  @Getter
  @Setter
  private Boolean onlineEnabled;

  @Getter
  @Setter
  private String type = "cachedFeaturegroupDTO";

  @Getter
  @Setter
  private TimeTravelFormat timeTravelFormat = TimeTravelFormat.HUDI;

  @Getter
  @Setter
  protected String location;

  @JsonIgnore
  // These are only used in the client. In the server they are aggregated in the `features` field
  private List<String> primaryKeys;

  @JsonIgnore
  // These are only used in the client. In the server they are aggregated in the `features` field
  private List<String> partitionKeys;

  @JsonIgnore
  // This is only used in the client. In the server they are aggregated in the `features` field
  private String hudiPrecombineKey;

  private FeatureGroupEngine featureGroupEngine = new FeatureGroupEngine();

  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureGroup.class);

  @Builder
  public FeatureGroup(FeatureStore featureStore, @NonNull String name, Integer version, String description,
                      List<String> primaryKeys, List<String> partitionKeys, String hudiPrecombineKey,
                      boolean onlineEnabled, TimeTravelFormat timeTravelFormat, List<Feature> features,
                      Boolean statisticsEnabled, Boolean histograms, Boolean correlations,
                      List<String> statisticColumns) {
    this.featureStore = featureStore;
    this.name = name;
    this.version = version;
    this.description = description;
    this.primaryKeys = primaryKeys;
    this.partitionKeys = partitionKeys;
    this.hudiPrecombineKey = timeTravelFormat == TimeTravelFormat.HUDI ? hudiPrecombineKey : null;
    this.onlineEnabled = onlineEnabled;
    this.timeTravelFormat = timeTravelFormat != null ? timeTravelFormat : TimeTravelFormat.HUDI;
    this.features = features;
    this.statisticsEnabled = statisticsEnabled != null ? statisticsEnabled : true;
    this.histograms = histograms;
    this.correlations = correlations;
    this.statisticColumns = statisticColumns;
  }

  public FeatureGroup() {
  }

  public Dataset<Row> read() throws FeatureStoreException, IOException {
    return read(false, null);
  }

  public Dataset<Row> read(boolean online) throws FeatureStoreException, IOException {
    return selectAll().read(online);
  }

  public Dataset<Row> read(Map<String, String> readOptions) throws FeatureStoreException, IOException {
    return read(false, null);
  }

  public Dataset<Row> read(boolean online, Map<String, String> readOptions) throws FeatureStoreException, IOException {
    return selectAll().read(online, readOptions);
  }

  /**
   * Reads Feature group data at a specific point in time.
   *
   * @param wallclockTime
   * @return DataFrame.
   * @throws FeatureStoreException
   * @throws IOException
   */
  public Dataset<Row> read(String wallclockTime) throws FeatureStoreException, IOException {
    return selectAll().asOf(wallclockTime).read(false, null);
  }

  /**
   * Reads Feature group data at a specific point in time.
   *
   * @param wallclockTime
   * @param readOptions
   * @return DataFrame.
   * @throws FeatureStoreException
   * @throws IOException
   */
  public Dataset<Row> read(String wallclockTime, Map<String, String> readOptions)
      throws FeatureStoreException, IOException {
    return selectAll().asOf(wallclockTime).read(false, readOptions);
  }

  /**
   * Reads changes that occurred between specified points in time.
   *
   * @param wallclockStartTime start date.
   * @param wallclockEndTime   end date.
   * @return DataFrame.
   * @throws FeatureStoreException
   * @throws IOException
   */
  public Dataset<Row> readChanges(String wallclockStartTime, String wallclockEndTime)
      throws FeatureStoreException, IOException {
    return selectAll().pullChanges(wallclockStartTime, wallclockEndTime).read(false, null);
  }

  /**
   * Reads changes that occurred between specified points in time.
   *
   * @param wallclockStartTime start date.
   * @param wallclockEndTime   end date.
   * @return DataFrame.
   * @throws FeatureStoreException
   * @throws IOException
   */
  public Dataset<Row> readChanges(String wallclockStartTime, String wallclockEndTime, Map<String, String> readOptions)
      throws FeatureStoreException, IOException {
    return selectAll().pullChanges(wallclockStartTime, wallclockEndTime).read(false, readOptions);
  }


  public void show(int numRows) throws FeatureStoreException, IOException {
    show(numRows, false);
  }

  public void show(int numRows, boolean online) throws FeatureStoreException, IOException {
    read(online).show(numRows);
  }

  public void save(Dataset<Row> featureData) throws FeatureStoreException, IOException {
    save(featureData, null);
  }

  public void save(Dataset<Row> featureData, Map<String, String> writeOptions)
      throws FeatureStoreException, IOException {
    featureGroupEngine.saveFeatureGroup(this, featureData, primaryKeys, partitionKeys, hudiPrecombineKey,
        writeOptions);
    if (statisticsEnabled) {
      statisticsEngine.computeStatistics(this, featureData);
    }
  }

  public void insert(Dataset<Row> featureData) throws IOException, FeatureStoreException {
    insert(featureData, null, false);
  }

  public void insert(Dataset<Row> featureData,  Map<String, String> writeOptions)
      throws FeatureStoreException, IOException {
    insert(featureData, null, false, null, writeOptions);
  }

  public void insert(Dataset<Row> featureData, Storage storage) throws IOException, FeatureStoreException {
    insert(featureData, storage, false, null, null);
  }

  public void insert(Dataset<Row> featureData, boolean overwrite) throws IOException, FeatureStoreException {
    insert(featureData, null, overwrite);
  }

  public void insert(Dataset<Row> featureData, Storage storage, boolean overwrite)
      throws IOException, FeatureStoreException {
    insert(featureData, storage, overwrite, null, null);
  }

  public void insert(Dataset<Row> featureData, boolean overwrite, Map<String, String> writeOptions)
      throws FeatureStoreException, IOException {
    insert(featureData, null, overwrite, null, writeOptions);
  }

  /**
   * Commit insert or upsert to time travel enabled Feature group.
   *
   * @param featureData dataframe to be committed.
   * @param operation   commit operation type, INSERT or UPSERT.
   * @throws FeatureStoreException
   * @throws IOException
   */
  public void insert(Dataset<Row> featureData, HudiOperationType operation)
      throws FeatureStoreException, IOException {
    insert(featureData, null, false, operation, null);
  }

  public void insert(Dataset<Row> featureData, Storage storage, boolean overwrite, HudiOperationType operation,
                     Map<String, String> writeOptions)
      throws FeatureStoreException, IOException {

    // operation is only valid for time travel enabled feature group
    if (operation != null && this.timeTravelFormat == TimeTravelFormat.NONE) {
      throw new IllegalArgumentException("operation argument is valid only for time travel enable feature groups");
    }

    if (operation == null && this.timeTravelFormat == TimeTravelFormat.HUDI) {
      if (overwrite) {
        operation = HudiOperationType.BULK_INSERT;
      } else {
        operation = HudiOperationType.UPSERT;
      }
    }

    featureGroupEngine.saveDataframe(this, featureData, storage,
        overwrite ? SaveMode.Overwrite : SaveMode.Append, operation, writeOptions);

    computeStatistics();
  }

  public void commitDeleteRecord(Dataset<Row> featureData)
      throws FeatureStoreException, IOException {

    // operation is only valid for time travel enabled feature group
    if (this.timeTravelFormat == TimeTravelFormat.NONE) {
      throw new FeatureStoreException("delete function is only valid for "
          + "time travel enabled feature group");
    }

    featureGroupEngine.commitDelete(this, featureData, null);
  }

  public void commitDeleteRecord(Dataset<Row> featureData, Map<String, String> writeOptions)
      throws FeatureStoreException, IOException {

    // operation is only valid for time travel enabled feature group
    if (this.timeTravelFormat == TimeTravelFormat.NONE) {
      throw new FeatureStoreException("delete function is only valid for "
          + "time travel enabled feature group");
    }

    featureGroupEngine.commitDelete(this, featureData, writeOptions);
  }

  /**
   * Return commit details.
   *
   * @throws FeatureStoreException
   * @throws IOException
   */
  public Map<String, Map<String, String>> commitDetails() throws IOException, FeatureStoreException {
    return featureGroupEngine.commitDetails(this, null);
  }

  /**
   * Return commit details.
   *
   * @param limit number of commits to return.
   * @throws FeatureStoreException
   * @throws IOException
   */
  public Map<String, Map<String, String>> commitDetails(Integer limit) throws IOException, FeatureStoreException {
    return featureGroupEngine.commitDetails(this, limit);
  }
}
