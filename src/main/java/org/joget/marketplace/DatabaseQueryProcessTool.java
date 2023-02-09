package org.joget.marketplace;

import java.util.Map;
import org.joget.plugin.base.DefaultApplicationPlugin;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import javax.sql.DataSource;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

public class DatabaseQueryProcessTool extends DefaultApplicationPlugin {
    private final static String MESSAGE_PATH = "messages/databaseQueryProcessTool";
    
    @Override
    public Collection execute(Map props) {
        Connection con = null;
        PreparedStatement stmt = null;
        String query = (String) props.get("query");
        boolean debugMode = Boolean.parseBoolean((String)props.get("debugMode"));
        
        if(debugMode){
            LogUtil.info(getClass().getName(), "Query: " + query);
        }
        
        Collection rows = new ArrayList();
        
        try {
            // retrieve connection from the default datasource
            DataSource ds = (DataSource)AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();

            // execute SQL query
            if(!con.isClosed()) {
                stmt = con.prepareStatement(query);
                ResultSet rs = stmt.executeQuery();
                
                ResultSetMetaData md = rs.getMetaData();
                int columns = md.getColumnCount();
                
                while (rs.next()) {
                    Map<String, String> row = new HashMap<String, String>(columns);
                    for(int i = 1; i <= columns; ++i){
                        row.put(md.getColumnName(i), rs.getString(i));
                    }
                    rows.add(row);
                }
            }
        } catch(Exception e) {
            LogUtil.error(DatabaseQueryProcessTool.class.getName(), e, "");
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
            } catch(Exception e){}
            try {
                if (con != null) {
                    con.close();
                }
            } catch(Exception e){

            }
        }
        
        if(debugMode){
            LogUtil.info(getClass().getName(), "Rows returned: " + rows);
        }
        
        return rows;
    }

    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.DatabaseQueryProcessTool", getClassName(), MESSAGE_PATH);
    }

    public String getVersion() {
        return "7.0.6";
    }

    public String getDescription() {
        return AppPluginUtil.getMessage("org.joget.marketplace.DatabaseQueryProcessTool.desc", getClassName(), MESSAGE_PATH);
    }

    public String getLabel() {
        return AppPluginUtil.getMessage("org.joget.marketplace.DatabaseQueryProcessTool", getClassName(), MESSAGE_PATH);
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{getLabel(), appId, appVersion};
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/databaseQueryProcessTool.json", arguments, true, MESSAGE_PATH);
        return json;
    }
}
