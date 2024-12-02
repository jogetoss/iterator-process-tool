package org.joget.marketplace;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListColumnFormat;
import org.joget.apps.datalist.service.DataListService;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

public class IteratorProcessToolUtility {
    
    protected static DataList getDataList(String datalistId) throws BeansException {
        ApplicationContext ac = AppUtil.getApplicationContext();
        DataListService dataListService = (DataListService) ac.getBean("dataListService");
        DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) ac.getBean("datalistDefinitionDao");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(datalistId, appDef);
        DataList datalist = null;
        
        if (datalistDefinition != null) {
            datalist = dataListService.fromJson(datalistDefinition.getJson());
        }
        
        return datalist;
    }
    
    protected static String getBinderFormattedValue(DataList dataList, Object row, String columnName){
        DataListColumn[] columns = dataList.getColumns();
        for (DataListColumn c : columns) {
            if(c.getName().equalsIgnoreCase(columnName)){
                String value = DataListService.evaluateColumnValueFromRow(row, columnName).toString();
                Collection<DataListColumnFormat> formats = c.getFormats();
                if (formats != null) {
                    for (DataListColumnFormat f : formats) {
                        if (f != null) {
                            value = f.format(dataList, c, row, value);
                            return value;
                        }else{
                            return value;
                        }
                    }
                }else{
                    return value;
                }
            }
        }
        return "";        
    }
    
    protected static String replaceValueFromIteratorRecord(String expr, Map row, DataList dataList) {
        Pattern pattern = Pattern.compile("\\{([a-zA-Z0-9_\\.]+)\\}");
        Matcher matcher = pattern.matcher(expr);
        
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = null;
            
            try{
                if(dataList != null){
                    value = getBinderFormattedValue(dataList, row, key);
                }else{
                    value = (String)row.get(key);
                }
            }catch(Exception ex){
                LogUtil.info("org.joget.marketplace.IteratorProcessToolUtility" , "Cannot get key [" + key + "]");
            }
            
            if (value != null) {
                expr = expr.replaceAll(StringUtil.escapeRegex("{"+key+"}"), value);
            }
        }
        
        return expr;
    }
    
    protected static Map replaceValueHashMap(Map map, String recordId, WorkflowAssignment assignment, Map iteratorRecord, DataList dataList){
        Iterator it = map.entrySet().iterator();
        Map returnMap = new HashMap();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry)it.next();
            
            if(pair.getValue() instanceof String){
                String replacedValue = (String)pair.getValue();
                replacedValue = replacedValue.replaceAll("\\$\\$", "#");
                if(recordId != null){
                    replacedValue = replacedValue.replaceAll("@recordId@", recordId);
                }
                
                replacedValue = replaceValueFromIteratorRecord(replacedValue, iteratorRecord, dataList);
                
                
                replacedValue = AppUtil.processHashVariable(replacedValue, assignment, (String)null, (Map)null);
                returnMap.put(pair.getKey(), replacedValue);
            }else if(pair.getValue() instanceof Object[]){
                Object[] objects = (Object[])pair.getValue();
                Object[] newObjects = new Object[objects.length];

                int i = 0;
                for(Object obj : objects){
                    Map temp = (Map) obj;
                    temp = replaceValueHashMap(temp, recordId, assignment, iteratorRecord, dataList);
                    newObjects[i] = temp;
                    i++;
                }
                
                returnMap.put(pair.getKey(), newObjects);
            }else if(pair.getValue() instanceof HashMap){
                returnMap.put(pair.getKey(), replaceValueHashMap((HashMap)pair.getValue(), recordId, assignment, iteratorRecord, dataList));
            }else{
                returnMap.put(pair.getKey(), pair.getValue());
            }
        }
        return returnMap;
    }
}
