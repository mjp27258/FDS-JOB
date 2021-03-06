package iquantex.com.permission.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import iquantex.com.dolphinscheduler.pojo.ProcessDefinition;
import iquantex.com.dolphinscheduler.pojo.Result;
import iquantex.com.entity.*;
import iquantex.com.entity.dependent.DependParameters;
import iquantex.com.entity.shell.ShellParameters;
import iquantex.com.entity.stroedprodure.StoredProcedureParameters;
import iquantex.com.enums.TaskType;
import iquantex.com.upgrade.BuildTask;
import iquantex.com.upgrade.InstanceTask;
import iquantex.com.utils.Constant;
import iquantex.com.utils.ParamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static iquantex.com.dolphinscheduler.utils.RandomUtil.randomInteger;
import static iquantex.com.utils.HttpUtil.executeResult;
import static iquantex.com.utils.ParamUtils.getInstanceEnv;

/**
 * @ClassName SheetParamConvert
 * @Description TODO
 * @Author jianping.mu
 * @Date 2020/11/28 2:10 下午
 * @Version 1.0
 * 解析excel to task
 */
public class ParamConvert {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParamConvert.class);
    private List<LocalParams> localParamsList;

    private final SheetParam sheetParam;
    private boolean flag = true;
    private final JSONObject locations;
    private final JSONArray taskTypeArr;
    private final List<Connects> connectsList;
    private final StringBuffer dependenceIdAll;
    private long timeMillis;

    public ParamConvert(SheetParam sheetParam) {
        this.sheetParam = sheetParam;
        this.taskTypeArr = new JSONArray();
        this.locations = new JSONObject();
        this.connectsList = new ArrayList<>();
        this.dependenceIdAll = new StringBuffer(5);
        call();
    }

    public void startTask(){

    }


    /**
     * 解析excel对象信息
     */
    public void call() {
        LOGGER.info("开始解析excel中的任务，任务名为:{}",sheetParam.getTableName());
        this.timeMillis = System.currentTimeMillis();
        String taskParam = sheetParam.getTaskParam();
        if (Objects.nonNull(taskParam)) {
            localParamsList = ParamUtils.taskParamToList(taskParam);
        }
        Connects connects = new Connects();
        List<HashMap<String, Boolean>> targetarr = new ArrayList<>();
        analysisType(sheetParam.getTaskType(), connects, targetarr);
        flag = false;

        if (Objects.nonNull(sheetParam.getTaskPath())) {
            analysisType(TaskType.SHELL.name(), connects, targetarr);
        }

        String dependType = sheetParam.getDependType();
        if (Objects.nonNull(dependType) && Objects.nonNull(sheetParam.getDepend())) {
            analysisType(dependType, connects, targetarr);
        }
        String dependentId = null;
        String dependentName = null;
        //遍历任务的依赖，封装location参数
        for (Map<String, Boolean> tar :
                targetarr) {
            for (String key : tar.keySet()) {
                boolean value = tar.get(key);
                String[] taskInfo = key.split("\\|");
                String taskId = taskInfo[0];
                String taskName = taskInfo[1];
                if (value) {
                    dependentId = taskId;
                    dependentName = taskName;
                } else {
                    dependenceIdAll.append(taskId).append(",");
                    locations.fluentPutAll(getLocation(taskId, taskName, false));
                }
            }
        }

        locations.fluentPutAll(getLocation(dependentId, dependentName, true));
        setTaskParameters();
        new SubProcessTaskImpl(sheetParam).convertToData();
    }

    /**
     * 设置task任务参数
     */
    public void setTaskParameters() {
        TaskParameters taskParameters = new TaskParameters();
        taskParameters.setGlobalParams(new ArrayList<>());
        taskParameters.setTasks(taskTypeArr);
        taskParameters.setTenantId(new InstanceTask().getTenantId());
        taskParameters.setTimeout(sheetParam.getAlarmTime());

        String jsonString = JSONObject.toJSONString(taskParameters, SerializerFeature.WriteMapNullValue);
        String workflowName = sheetParam.getSubApplication() + "." + sheetParam.getTableName();
        Result result = commitTask(jsonString, workflowName);
        executeResult(result);
    }

    /**
     * 提交task到ds
     *
     * @param json
     * @param taskName
     * @return
     */
    public Result commitTask(String json, String taskName) {
        LOGGER.info("【commitTask】开始创建名字为{}任务。", taskName);
        ProcessDefinition processDefinition = new ProcessDefinition();
        processDefinition.setConnects(JSONObject.toJSONString(connectsList));
        processDefinition.setDescription(sheetParam.getDescription());
        processDefinition.setGlobalParams("[]");
        processDefinition.setName(taskName);
        processDefinition.setProcessDefinitionJson(json);
        processDefinition.setLocations(locations.toJSONString());
        LOGGER.info("初始化任务总耗时：{}", (System.currentTimeMillis() - timeMillis) / 1000 + "s");
        return new BuildTask(getInstanceEnv()).getCreateWorkStat(processDefinition);
    }


    /**
     * 执行不同分支
     *
     * @param type      任务分类
     * @param connects  任务参数
     * @param targetarr 任务画布参数
     */
    public void analysisType(String type, Connects connects, List<HashMap<String, Boolean>> targetarr) {
        HashMap<String, Boolean> map = new HashMap<>(3);
        switch (TaskType.valueOf(type.toUpperCase())) {
            case SHELL:
                ShellParameters shellParameters = new ShellTaskImpl(sheetParam,
                        localParamsList, new ShellParameters(), flag).convertToData();
                String shellId = shellParameters.getId();
                String shellName = shellParameters.getName();
                map.put(shellId + "|" + shellName, flag);
                getLocationConnect(connects, shellId);
                taskTypeArr.fluentAdd(JSONArray.toJSON(shellParameters));
                break;
            case DEPENDENT:
                DependParameters dependParameters = new DependentTaskImpl(sheetParam,
                        new DependParameters(),flag).convertToData();
                String dependId = dependParameters.getId();
                String dependName = dependParameters.getName();
                map.put(dependId + "|" + dependName, false);
                getLocationConnect(connects, dependId);
                taskTypeArr.fluentAdd(JSONArray.toJSON(dependParameters));
                break;
            case PROCEDURE:
                StoredProcedureParameters storedProcedureParameters = new StoreProducerTaskImpl(sheetParam,
                        localParamsList, new StoredProcedureParameters(), flag).convertToData();
                String procedureId = storedProcedureParameters.getId();
                String procedureName = storedProcedureParameters.getName();
                map.put(procedureId + "|" + procedureName, flag);
                getLocationConnect(connects, procedureId);
                taskTypeArr.fluentAdd(JSONArray.toJSON(storedProcedureParameters));
                break;
            default:
                LOGGER.error("该任务类型不存在：{}", type.toUpperCase());
                break;
        }
        targetarr.add(map);
    }

    /**
     * 初始化作业信息 location部分
     *
     * @param taskId
     * @param taskName
     * @return
     */
    public JSONObject getLocation(String taskId, String taskName, boolean flag) {
        Location location = new Location();
        JSONObject jsonLocation = new JSONObject();
        if (!flag) {
            location.setNodenumber("1");
        } else {
            location.setTargetarr(dependenceIdAll.substring(0, dependenceIdAll.length() - 1));
        }
        location.setX(randomInteger(Constant.NUMBER));
        location.setY(randomInteger(Constant.NUMBER));
        location.setName(taskName);

        jsonLocation.put(taskId, JSONObject.toJSON(location));
        return jsonLocation;
    }


    /**
     * 设置connect参数
     *
     * @param connects
     * @param id
     */
    public void getLocationConnect(Connects connects, String id) {
        if (flag) {
            connects.setEndPointTargetId(id);
        } else {
            String endPointTargetId = connects.getEndPointTargetId();
            connects = new Connects();
            connects.setEndPointTargetId(endPointTargetId);
            connects.setEndPointSourceId(id);
            connectsList.add(connects);
        }
    }

}
