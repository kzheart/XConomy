/*
 *  This file (DataBaseConfig.java) is a part of project XConomy
 *  Copyright (C) YiC and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package me.yic.xconomy.info;

import com.zaxxer.hikari.HikariDataSource;
import me.yic.xconomy.AdapterManager;
import me.yic.xconomy.XConomy;
import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.adapter.comp.CConfig;
import me.yic.xconomy.lang.MessagesManager;

public class DataBaseConfig {

    public static CConfig config;

    public void Initialization() {
        canasync = !XConomyLoad.Config.DISABLE_CACHE;
        if (isPostgreSQL()) {
            String suffix = gettablesuffix().replace("%sign%", XConomyLoad.Config.SYNCDATA_SIGN);
            if (!suffix.matches("[a-zA-Z0-9_]{0,45}"))
                throw new IllegalArgumentException("PostgreSQL.table-suffix 仅允许字母、数字和下划线，最多 45 个字符");
        }
        setHikariConnectionPooling();
    }

    public boolean EnableConnectionPool = false;
    public boolean canasync = false;

    public final String ENCODING = config.getString("MySQL.property.encoding");


    public int getStorageType() {
        if (config.getString("Settings.storage-type").equalsIgnoreCase("MySQL")) {
            return 2;
        }else if (config.getString("Settings.storage-type").equalsIgnoreCase("MariaDB")) {
            return 3;
        }
        if (config.getString("Settings.storage-type").equalsIgnoreCase("PostgreSQL")
                || config.getString("Settings.storage-type").equalsIgnoreCase("PG")) return 4;
        if (!config.getString("Settings.storage-type").equalsIgnoreCase("SQLite"))
            throw new IllegalArgumentException("未知 storage-type，支持 SQLite/MySQL/MariaDB/PostgreSQL");
        return 1;
    }

    public void setHikariConnectionPooling() {
        if (config.getBoolean("Settings.usepool")) {
            try {
                Class.forName("org.slf4j.Logger");
                if (getStorageType() == 0 || getStorageType() == 1) {
                    EnableConnectionPool = false;
                }else {
                    try {
                        new HikariDataSource();
                        EnableConnectionPool = !AdapterManager.foundvaultpe;
                    } catch (UnsupportedClassVersionError e) {
                        EnableConnectionPool = false;
                        XConomy.getInstance().logger("connection-pool-unsupport", 1, null);
                    }
                }
            } catch (ClassNotFoundException e) {
                XConomy.getInstance().logger("未找到 'org.slf4j.Logger'", 1, null);
                EnableConnectionPool = false;
            }

            if (!EnableConnectionPool){
                XConomy.getInstance().logger("连接池未启用", 0, null);
            }
        }
    }

    public boolean isMySQL() {
        return getStorageType() == 2 || getStorageType() == 3;
    }

    public boolean isPostgreSQL() { return getStorageType() == 4; }

    public boolean isServerDatabase() { return isMySQL() || isPostgreSQL(); }

    public String gethost() {
        if (isPostgreSQL()) return config.getString("PostgreSQL.host");
        if (getStorageType() == 1) {
            return config.getString("SQLite.path");
        } else if (getStorageType() == 2 || getStorageType() == 3) {
            return config.getString("MySQL.host");
        }
        return "";
    }

    public String getuser() {
        if (isPostgreSQL()) return config.getString("PostgreSQL.user");
        if (getStorageType() == 2 || getStorageType() == 3) {
            return config.getString("MySQL.user");
        }
        return "";
    }

    public String getpass() {
        if (isPostgreSQL()) return config.getString("PostgreSQL.pass");
        if (getStorageType() == 2 || getStorageType() == 3) {
            return config.getString("MySQL.pass");
        }
        return "";
    }

    public String gettablesuffix() {
        if (isPostgreSQL()) return config.getString("PostgreSQL.table-suffix");
        if (getStorageType() == 2 || getStorageType() == 3) {
            return config.getString("MySQL.table-suffix");
        }
        return "";
    }


    public String geturl() {
        if (isPostgreSQL()) {
            String explicit = config.getString("PostgreSQL.jdbc-url");
            if (explicit != null && !explicit.isEmpty()) {
                if (!explicit.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException("PostgreSQL.jdbc-url 必须使用 jdbc:postgresql");
                return explicit;
            }
            return "jdbc:postgresql://" + config.getString("PostgreSQL.host") + ":"
                    + config.getString("PostgreSQL.port") + "/" + config.getString("PostgreSQL.database")
                    + "?sslmode=" + config.getString("PostgreSQL.sslmode") + "&connectTimeout=5&socketTimeout=10";
        }
        if (getStorageType() == 2 || getStorageType() == 3) {
            String url = "jdbc:mysql://";
            if (getStorageType() == 3){
                url = "jdbc:mariadb://";
            }
            url += config.getString("MySQL.host")
                    + ":" + config.getString("MySQL.port") + "/"
                    + config.getString("MySQL.database") + "?characterEncoding="
                    + ENCODING + "&useSSL="
                    + config.getString("MySQL.property.usessl");
            if (config.getString("MySQL.property.timezone") != null &&
                    !config.getString("MySQL.property.timezone").equals("")) {
                url = url + "&serverTimezone=" + config.getString("MySQL.property.timezone");
            }
            if (config.getBoolean("MySQL.property.allowPublicKeyRetrieval")) {
                url = url + "&allowPublicKeyRetrieval=true";
            }
            return url;
        }
        return "";
    }

    public void loggersysmess(String tag) {
        String mess = MessagesManager.systemMessage(tag);
        switch (getStorageType()) {
            case 1:
                XConomy.getInstance().logger(null, 0, mess.replace("%type%", "SQLite"));
                break;
            case 2:
                XConomy.getInstance().logger(null, 0, mess.replace("%type%", "MySQL"));
                break;
            case 3:
                XConomy.getInstance().logger(null, 0, mess.replace("%type%", "MariaDB"));
                break;
            case 4:
                XConomy.getInstance().logger(null, 0, mess.replace("%type%", "PostgreSQL"));
                break;
        }
    }

}