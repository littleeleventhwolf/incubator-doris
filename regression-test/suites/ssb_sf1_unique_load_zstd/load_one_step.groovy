// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.


suite("load_one_step") {
    def tables = ["customer": ["""c_custkey,c_name,c_address,c_city,c_nation,c_region,c_phone,c_mktsegment,no_use""", 30000], "lineorder": ["""lo_orderkey,lo_linenumber,lo_custkey,lo_partkey,lo_suppkey,lo_orderdate,lo_orderpriority, 
                    lo_shippriority,lo_quantity,lo_extendedprice,lo_ordtotalprice,lo_discount, 
                    lo_revenue,lo_supplycost,lo_tax,lo_commitdate,lo_shipmode,lo_dummy""", 6001215], "part": ["""p_partkey,p_name,p_mfgr,p_category,p_brand,p_color,p_type,p_size,p_container,p_dummy""", 200000], "date": ["""d_datekey,d_date,d_dayofweek,d_month,d_year,d_yearmonthnum,d_yearmonth,
                    d_daynuminweek,d_daynuminmonth,d_daynuminyear,d_monthnuminyear,d_weeknuminyear,
                    d_sellingseason,d_lastdayinweekfl,d_lastdayinmonthfl,d_holidayfl,d_weekdayfl,d_dummy""", 2556], "supplier": ["""s_suppkey,s_name,s_address,s_city,s_nation,s_region,s_phone,s_dummy""", 2000]]

    tables.each { tableName, rows ->
        sql """ DROP TABLE IF EXISTS $tableName """
        sql new File("""${context.file.parent}/ddl/${tableName}_create.sql""").text
        streamLoad {
            table tableName
            set 'column_separator', '|'
            set 'compress_type', 'GZ'
            set 'columns', rows[0]
            file """${context.sf1DataPath}/ssb/sf1/${tableName}.tbl.gz"""

            time 10000 // limit inflight 10s

            // stream load action will check result, include Success status, and NumberTotalRows == NumberLoadedRows

            // if declared a check callback, the default check condition will ignore.
            // So you must check all condition
            check { result, exception, startTime, endTime ->
                if (exception != null) {
                    throw exception
                }
                log.info("Stream load result: ${result}".toString())
                def json = parseJson(result)
                assertEquals("success", json.Status.toLowerCase())
                assertEquals(json.NumberTotalRows, json.NumberLoadedRows)
                assertTrue(json.NumberLoadedRows > 0 && json.LoadBytes > 0)
            }
        }
        sql 'sync'
        for (int i = 1; i <= 5; i++) {
            def loadRowCount = sql "select count(1) from ${tableName}"
            logger.info("select ${tableName} numbers: ${loadRowCount[0][0]}".toString())
            assertTrue(loadRowCount[0][0] == rows[1])
        }
    }
}
