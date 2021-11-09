package org.joget.marketplace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.plugin.base.ApplicationPlugin;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

public class IteratorProcessToolRecord extends DefaultApplicationPlugin{
    private final static String MESSAGE_PATH = "messages/iteratorProcessTool";
    
    @Override
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.IteratorProcessTool.recordMode", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getVersion() {
        return "7.0.2";
    }

    @Override
    public String getDescription() {
        return AppPluginUtil.getMessage("org.joget.marketplace.IteratorProcessTool.recordMode.desc", getClassName(), MESSAGE_PATH);
    }
    
    @Override
    public String getLabel() {
        return AppPluginUtil.getMessage("org.joget.marketplace.IteratorProcessTool.recordMode", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }
    
    @Override
    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{getLabel(), appId, appVersion};
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/iteratorProcessTool.json", arguments, true, MESSAGE_PATH);
        return json;
    }

    @Override
    public Object execute(final Map properties) {
        final String processToolPropertyName = "executeProcessTool";
        final boolean debugMode = Boolean.parseBoolean((String)properties.get("debug"));
        final boolean delay = Boolean.parseBoolean((String)properties.get("delay"));
        final PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        final AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        final WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        final AppDefinition appDef = (AppDefinition) properties.get("appDef");
        String columnId = getPropertyString("iteratorColumnId");
        
        if(columnId.isEmpty()){
            columnId = getPropertyString("iteratorToolId");
        }
        
        DataList datalist = null;
        
        Collection recordIds = new ArrayList();
        
        if(debugMode){
            LogUtil.debug(getClass().getName(), "Executing Iterator");
        }
        
        //get iterator
        String iteratorMethod = (String)properties.get("iteratorMethod");
        if(iteratorMethod.equalsIgnoreCase("Process Tool")){
            
            Object iteratorProcessTool = properties.get("iteratorProcessTool");
            if (iteratorProcessTool != null && iteratorProcessTool instanceof Map) {
                Map fvMap = (Map) iteratorProcessTool;
                if (fvMap != null && fvMap.containsKey("className") && !fvMap.get("className").toString().isEmpty()) {
                    String className = fvMap.get("className").toString();
                    ApplicationPlugin iteratorPlugin = (ApplicationPlugin)pluginManager.getPlugin(className);

                    //obtain plugin defaults
                    Map propertiesMap = new HashMap();
                    propertiesMap.putAll(AppPluginUtil.getDefaultProperties((Plugin) iteratorPlugin, (Map) fvMap.get("properties"), null, null));

                    if (iteratorPlugin instanceof PropertyEditable) {
                        ((PropertyEditable) iteratorPlugin).setProperties(propertiesMap);
                    }

                    recordIds = (Collection<Map>)iteratorPlugin.execute(propertiesMap);
                }
            }
            
        }else if(iteratorMethod.equalsIgnoreCase("Datalist")){
            
            String datalistId = getPropertyString("datalistId");
            datalist = IteratorProcessToolUtility.getDataList(datalistId);
            
            if(datalist != null){
                recordIds = datalist.getRows(datalist.getTotal(), 0);
            }
            
        }

        if(debugMode){
            LogUtil.debug(getClass().getName(), "Iterator returned: " + recordIds.size() + " items: " + recordIds.toString());
        }
        
        //iterate thru activity assignment records one by one
        int recordCount = 0;
        for(Object recordIdObj : recordIds){
            recordCount++;
            
            final Map recordIdMap = (Map)recordIdObj;
            final String recordId = (String)recordIdMap.get(columnId);
            final DataList dl = datalist;
            
            if(debugMode){
                LogUtil.debug(getClass().getName(), "Iterating item: " + recordCount + " - Record: " + recordId);
            }
            
            if (recordId != null && !recordId.isEmpty()) {
                new PluginThread(new Runnable() {
                    public void run() {
                        AppUtil.setCurrentAppDefinition(appDef);
                        
                        //note: we do not use wfAssignment from the main context. we use recordId as the context.
                        WorkflowAssignment assignment;
                        
                        //attempt to set mock assignment - works when recordId is activityId
                        assignment = workflowManager.getMockAssignment(recordId);
                        
                        //if assignment is not available, create a new one for the purpose of retrieving primary key
                        if(assignment == null){
                            assignment = new WorkflowAssignment();
                            assignment.setProcessId(recordId);
                        }
                        
                        //fire plugins one by one per record
                        Object objProcessTool = properties.get(processToolPropertyName);
                        if (objProcessTool != null && objProcessTool instanceof Map) {
                            Map fvMap = (Map) objProcessTool;
                            if (fvMap != null && fvMap.containsKey("className") && !fvMap.get("className").toString().isEmpty()) {
                                String className = fvMap.get("className").toString();
                                ApplicationPlugin appPlugin = (ApplicationPlugin)pluginManager.getPlugin(className);
                                Map propertiesMap = (Map) fvMap.get("properties");

                                //obtain plugin defaults
                                propertiesMap.putAll(AppPluginUtil.getDefaultProperties((Plugin) appPlugin, (Map) fvMap.get("properties"), appDef, assignment));

                                if(debugMode){
                                    LogUtil.debug(getClass().getName(), "Executing tool: " + processToolPropertyName + " - " + className);
                                }

                                //replace recordID inside the plugin's properties
                                Map propertiesMapWithRecordID = IteratorProcessToolUtility.replaceValueHashMap(propertiesMap, recordId, assignment, recordIdMap, dl);

                                if(debugMode){
                                    LogUtil.debug(getClass().getName(), "Executing tool: " + processToolPropertyName + " - " + className);
                                }

                                //inject additional variables into the plugin
                                propertiesMapWithRecordID.put("workflowAssignment", assignment);
                                propertiesMapWithRecordID.put("appDef", appDef);
                                propertiesMapWithRecordID.put("pluginManager", pluginManager);

                                if (appPlugin instanceof PropertyEditable) {
                                    ((PropertyEditable) appPlugin).setProperties(propertiesMapWithRecordID);
                                }

                                Object result = appPlugin.execute(propertiesMapWithRecordID);

                                if(debugMode){
                                    if(result!=null){
                                        LogUtil.debug(getClass().getName(), "Executed tool: " + processToolPropertyName + " - " + className + " - " + result.toString());
                                    }else{
                                        LogUtil.debug(getClass().getName(), "Executed tool: " + processToolPropertyName + " - " + className);
                                    }
                                }

                                if(delay){
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException ex) {
                                        Logger.getLogger(IteratorProcessTool.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }
                        }
                    }
                }).start();

                if(delay){
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(IteratorProcessTool.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            if(debugMode){
                LogUtil.debug(getClass().getName(), "Finished item " + recordCount + " - Record: " + recordId);
            }
        }
        return null;
    }
 
    
}