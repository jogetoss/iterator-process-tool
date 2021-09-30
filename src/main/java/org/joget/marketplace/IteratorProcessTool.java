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
import org.joget.commons.util.PluginThread;
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
    private final static String MESSAGE_PATH = "messages/iteratorProcessTool";
    
    @Override
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.IteratorProcessTool.assignmentMode", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getVersion() {
        return "7.0.0";
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
    public Object execute(final Map properties) {
        final String processToolPropertyName = "executeProcessTool";
        final boolean debugMode = Boolean.parseBoolean((String)properties.get("debug"));
        final boolean delay = Boolean.parseBoolean((String)properties.get("delay"));
        final PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        final WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        final AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
        Collection iteratorRecords = new ArrayList();
        DataList datalist = null;
        String columnId = getPropertyString("iteratorColumnId");
        
        if(columnId.isEmpty()){
            columnId = getPropertyString("iteratorToolId");
        }
        
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
        
        if(debugMode){
            LogUtil.debug(getClass().getName(), "Iterator returned: " + iteratorRecords.size() + " items: " + iteratorRecords.toString());
        }
        
        //iterate thru activity assignment records one by one
        int recordCount = 0;
        for(Object iteratorRecord : iteratorRecords){
            
            recordCount++;
            
            final Map iteratorRecordMap = (Map)iteratorRecord;
            final String activityInstanceId = (String)iteratorRecordMap.get(columnId);
            final DataList dl = datalist;
            
            if(debugMode){
                LogUtil.debug(getClass().getName(), "Iterating item: " + recordCount + " - activity: " + activityInstanceId);
            }
            
            if (activityInstanceId != null && !activityInstanceId.isEmpty()) {
                WorkflowActivity wfActivity = workflowManager.getActivityById(activityInstanceId);
                
                if(wfActivity == null){
                    if(debugMode){
                        LogUtil.debug(getClass().getName(), "No activity found for activity: " + activityInstanceId);
                    }
                    
                    continue;
                }
                
                Collection assignees = workflowManager.getAssignmentResourceIds(wfActivity.getProcessDefId(), wfActivity.getProcessId(), activityInstanceId);
                
                if(assignees == null || assignees.isEmpty()){
                    if(debugMode){
                        LogUtil.debug(getClass().getName(), "Assignee not found for activity: " + activityInstanceId);
                    }
                    continue;
                }
                
                if(debugMode){
                    LogUtil.debug(getClass().getName(), "Found " + assignees.size() + " assignees: " + assignees.toString());
                }
                
                for(Object assigneeObj : assignees){
                    
                    final String assignee = (String)assigneeObj;
                    
                    if(debugMode){
                        LogUtil.debug(getClass().getName(), "Iterating assignee: " + assignee);
                    }
                    
                    new PluginThread(new Runnable() {
                        public void run() {
                            AppDefinition appDef = appService.getAppDefinitionForWorkflowActivity(activityInstanceId);
                            AppUtil.setCurrentAppDefinition(appDef);
                            WorkflowUserManager workflowUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
                            workflowUserManager.setCurrentThreadUser(assignee);
                            WorkflowAssignment assignment = workflowManager.getAssignment(activityInstanceId);
                            
                            //fire plugins one by one per acitivity per assignee by passing in activity and username into them
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
                                    Map propertiesMapWithHashParsed = IteratorProcessToolUtility.replaceValueHashMap(propertiesMap, "", assignment, iteratorRecordMap, dl);

                                    //inject additional variables into the plugin
                                    propertiesMapWithHashParsed.put("workflowAssignment", assignment);
                                    propertiesMapWithHashParsed.put("appDef", appDef);
                                    propertiesMapWithHashParsed.put("pluginManager", pluginManager);
                                    //propertiesMap.put("assignee", assignee);

                                    if (appPlugin instanceof PropertyEditable) {
                                        ((PropertyEditable) appPlugin).setProperties(propertiesMapWithHashParsed);
                                    }

                                    Object result = appPlugin.execute(propertiesMapWithHashParsed);

                                    if(debugMode){
                                        if(result!=null){
                                            LogUtil.debug(getClass().getName(), "Executed tool: " + processToolPropertyName + " - " + className + " - " + result.toString());
                                        }else{
                                            LogUtil.debug(getClass().getName(), "Executed tool: " + processToolPropertyName + " - " + className);
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
                    
                    if(debugMode){
                        LogUtil.debug(getClass().getName(), "Finished assignee: " + assignee);
                    }
                }
            
                if(debugMode){
                    LogUtil.debug(getClass().getName(), "Finished item " + recordCount + " - Activity: " + activityInstanceId);
                }
            
            }
            
            if(debugMode){
                LogUtil.debug(getClass().getName(), "Finished iterating item " + recordCount + " - Activity: " + activityInstanceId);
            }
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
    
    
    
}
