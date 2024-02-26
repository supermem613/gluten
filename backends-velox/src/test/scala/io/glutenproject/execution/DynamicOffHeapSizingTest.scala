/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.glutenproject.execution

import io.glutenproject.benchmarks.RandomParquetDataGenerator
import io.glutenproject.tags.SkipTestTags

import org.apache.spark.SparkConf

@SkipTestTags
class DynamicOffHeapSizingTest extends VeloxWholeStageTransformerSuite {
  override protected val backend: String = "velox"
  override protected val resourcePath: String = "/tpch-data-parquet-velox"
  override protected val fileFormat: String = "parquet"

  private val dataGenerator = RandomParquetDataGenerator(System.currentTimeMillis())
  private val outputPath = getClass.getResource("/").getPath + "fuzzer_output.parquet"

  private val AGG_SQL =
    """select f_1, count(DISTINCT f_1)
      |from tbl group
      |group by 1""".stripMargin

  override protected def sparkConf: SparkConf = {
    super.sparkConf
      .set("spark.plugins", "io.glutenproject.GlutenPlugin")
      .set("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
      .set("spark.executor.memory", "5GB")
      .set("spark.gluten.memory.dynamic.offHeap.sizing.enabled", "true")
      .set("spark.memory.offHeap.enabled", "false")
      .set("spark.driver.memory", "8GB")
      .set("spark.driver.maxResultSize", "50GB")
      .set("spark.gluten.sql.debug", "true")
      .set("spark.gluten.sql.columnar.backend.velox.glogSeverityLevel", "0")
  }

  def getRootCause(e: Throwable): Throwable = {
    if (e.getCause == null) {
      return e
    }
    getRootCause(e.getCause)
  }

  test("Dynamic Off-Heap Sizing") {
    System.gc()
    dataGenerator.generateRandomData(spark, outputPath)
    spark.read.format("parquet").load(outputPath).createOrReplaceTempView("tbl")
    val df = spark.sql(AGG_SQL)
    df
  }
}
