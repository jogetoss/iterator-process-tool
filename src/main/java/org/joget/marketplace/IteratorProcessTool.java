package org.joget.marketplace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.service.DataListService;
import org.joget.commons.util.LogUtil;
import org.joget.directory.model.User;
import org.joget.plugin.base.ApplicationPlugin;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowActivity;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

public class IteratorProcessTool extends DefaultApplicationPlugin implements PluginWebSupport{
    private static String MESSAGE_PATH = "messages/iteratorProcessTool";
    
    @Override
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.IteratorProcessTool.assignmentMode", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getVersion() {
        return "7.0.9";
    }

    @Override
    public String getDescription() {
        return AppPluginUtil.getMessage("org.joget.marketplace.IteratorProcessTool.assignmentMode.desc", getClassName(), MESSAGE_PATH);
    }
    
    @Override
    public String getLabel() {
        return AppPluginUtil.getMessage("org.joget.marketplace.IteratorProcessTool.assignmentMode", getClassName(), MESSAGE_PATH);
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
    public Object execute(Map properties) {
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
        WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        
        String processToolPropertyName = "executeProcessTool";
        String delayString = (String)properties.get("delay");
        
        int delayInt = 0;
        if(delayString.equalsIgnoreCase("true")){
            delayInt = 1;
        }else if(delayString.equalsIgnoreCase("false")){
            delayInt = 0;
        }else{
            delayInt = Integer.parseInt(delayString);
        }
        
        int delay = delayInt;
        
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        Collection iteratorRecords = new ArrayList();
        DataList datalist = null;
        String columnId = getPropertyString("iteratorColumnId");
        
        if(columnId.isEmpty()){
            columnId = getPropertyString("iteratorToolId");
        }
        
        debug(properties, getClass().getName(), "Executing Iterator");
        
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

                    propertiesMap = IteratorProcessToolUtility.replaceValueHashMap(propertiesMap, null, wfAssignment, null, null);
                    
                    if (iteratorPlugin instanceof PropertyEditable) {
                        ((PropertyEditable) iteratorPlugin).setProperties(propertiesMap);
                    }

                    iteratorRecords = (Collection<Map>)iteratorPlugin.execute(propertiesMap);
                }
            }
            
        }else if(iteratorMethod.equalsIgnoreCase("Datalist")){
            
            String datalistId = getPropertyString("datalistId");
            datalist = IteratorProcessToolUtility.getDataList(datalistId);
            
            if(datalist != null){
                iteratorRecords = datalist.getRows(datalist.getTotal(), 0);
            }
            
        }
        
        debug(properties, getClass().getName(), "Iterator returned: " + iteratorRecords.size() + " Items: " + iteratorRecords.toString());
        
        User currentUser = workflowUserManager.getCurrentUser();
        AppDefinition currentAppDef = AppUtil.getCurrentAppDefinition();
        
        int recordCount = 0;
        
        try{
            //iterate thru activity assignment records one by one
            for(Object iteratorRecord : iteratorRecords){

                recordCount++;

                String activityInstanceId = (String)((Map)iteratorRecord).get(columnId);

                debug(properties, getClass().getName(), "Iterating Item [" + recordCount + "] - Activity [" + activityInstanceId + "]");

                if (activityInstanceId != null && !activityInstanceId.isEmpty()) {
                    WorkflowActivity wfActivity = workflowManager.getActivityById(activityInstanceId);

                    if(wfActivity == null){
                        debug(properties, getClass().getName(), "No activity found for Activity [" + activityInstanceId + "]");
                        continue;
                    }

                    Collection assignees = workflowManager.getAssignmentResourceIds(wfActivity.getProcessDefId(), wfActivity.getProcessId(), activityInstanceId);

                    if(assignees == null || assignees.isEmpty()){
                        debug(properties, getClass().getName(), "Assignee not found for Activity [" + activityInstanceId + "]");
                        continue;
                    }

                    debug(properties, getClass().getName(), "Found [" + assignees.size() + "] - Assignees [" + assignees.toString() + "]");

                    for(Object assigneeObj : assignees){
                        String assignee = (String)assigneeObj;
                        debug(properties, getClass().getName(),  "Iterating Assignee [" + assignee + "]");

                        //trigger plugin per acitivity per assignee by passing in activity and username into them
                        AppDefinition appDef = appService.getAppDefinitionForWorkflowActivity(activityInstanceId);
                        AppUtil.setCurrentAppDefinition(appDef);

                        workflowUserManager.setCurrentThreadUser(assignee);
                        WorkflowAssignment assignment = workflowManager.getAssignment(activityInstanceId);


                        Object objProcessTool = properties.get(processToolPropertyName);
                        if (objProcessTool != null && objProcessTool instanceof Map) {
                            Map fvMap = (Map) objProcessTool;
                            if (fvMap != null && fvMap.containsKey("className") && !fvMap.get("className").toString().isEmpty()) {
                                String className = fvMap.get("className").toString();
                                ApplicationPlugin appPlugin = (ApplicationPlugin)pluginManager.getPlugin(className);
                                Map propertiesMap = (Map) fvMap.get("properties");

                                //obtain plugin defaults
                                propertiesMap.putAll(AppPluginUtil.getDefaultProperties((Plugin) appPlugin, (Map) fvMap.get("properties"), appDef, assignment));

                                debug(properties, getClass().getName(), "Executing Tool: " + processToolPropertyName + " - " + className);

                                //replace recordID inside the plugin's properties
                                Map propertiesMapWithHashParsed = IteratorProcessToolUtility.replaceValueHashMap(propertiesMap, "", assignment, (Map)iteratorRecord, datalist);

                                //inject additional variables into the plugin
                                propertiesMapWithHashParsed.put("data", iteratorRecord);
                                propertiesMapWithHashParsed.put("workflowAssignment", assignment);
                                propertiesMapWithHashParsed.put("appDef", appDef);
                                propertiesMapWithHashParsed.put("pluginManager", pluginManager);
                                //propertiesMap.put("assignee", assignee);

                                if (appPlugin instanceof PropertyEditable) {
                                    ((PropertyEditable) appPlugin).setProperties(propertiesMapWithHashParsed);
                                }

                                try{
                                    Object result = appPlugin.execute(propertiesMapWithHashParsed);

                                    if(result!=null){
                                        debug(properties, getClass().getName(), "Executed Tool [" + processToolPropertyName + "] - [" + className + "] - [" + result.toString() + "]");
                                    }else{
                                        debug(properties, getClass().getName(), "Executed Tool [" + processToolPropertyName + "] - [" + className + "]");
                                    }
                                }catch(Exception ex){
                                    LogUtil.error(getClass().getName(), ex, "Error Executing Tool [" + processToolPropertyName + "]");
                                }
                            }
                        }

                        if(delay > 0){
                            try {
                                Thread.sleep(delay * 1000);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(IteratorProcessTool.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        debug(properties, getClass().getName(), "Finished Assignee [" + assignee + "]");
                    }
                    debug(properties, getClass().getName(), "Finished Item [" + recordCount + "] - Activity [" + activityInstanceId + "]");
                }
                
                debug(properties, getClass().getName(), "Finished iterating Item [" + recordCount + "] - Activity [" + activityInstanceId + "]");
            }
        }catch(Exception ex){
            LogUtil.error(getClass().getName(), ex, "Error on Item [" + recordCount + "]");
        }finally{
            workflowUserManager.setCurrentThreadUser(currentUser);
            AppUtil.setCurrentAppDefinition(currentAppDef);
        }
        
        return null;
    }
    
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN);
        if (!isAdmin) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        
        String action = request.getParameter("action");
        if ("getDatalistColumns".equals(action)) {
            try {
                ApplicationContext ac = AppUtil.getApplicationContext();
                AppDefinition appDef = AppUtil.getCurrentAppDefinition();
                DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) ac.getBean("datalistDefinitionDao");
                DataListService dataListService = (DataListService) ac.getBean("dataListService");
                
                String datalistId = request.getParameter("id");
                DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);
                
                DataList datalist;
                if (datalistDefinition != null) {
                    datalist = dataListService.fromJson(datalistDefinition.getJson());
                    DataListColumn[] datalistcolumns = datalist.getColumns();
                    
                    //JSONObject jsonObject = new JSONObject();
                    JSONArray columns = new JSONArray();
                    for(int i = 0; i < datalistcolumns.length; i++ ){
                        JSONObject column = new JSONObject();
                        column.put("value", datalistcolumns[i].getName());
                        column.put("label", datalistcolumns[i].getLabel());
                        columns.put(column);
                    }
                    columns.write(response.getWriter());
                }else{
                    JSONArray columns = new JSONArray();
                    columns.write(response.getWriter());
                }
                
            } catch (Exception e) {
                LogUtil.error(IteratorProcessTool.class.getName(), e, "Webservice getColumns");
            } 
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }
    
    public static void debug(Map properties, String className, String message) {
        if (properties.get("debug") != null && "true".equals(properties.get("debug").toString())) {
            LogUtil.info(className, message);
        }
    }
    
}
