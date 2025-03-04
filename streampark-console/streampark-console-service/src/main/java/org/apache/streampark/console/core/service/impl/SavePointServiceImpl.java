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

package org.apache.streampark.console.core.service.impl;

import org.apache.streampark.common.util.Utils;
import org.apache.streampark.console.base.domain.Constant;
import org.apache.streampark.console.base.domain.RestRequest;
import org.apache.streampark.console.base.exception.InternalException;
import org.apache.streampark.console.base.mybatis.pager.MybatisPager;
import org.apache.streampark.console.base.util.CommonUtils;
import org.apache.streampark.console.core.entity.Application;
import org.apache.streampark.console.core.entity.FlinkEnv;
import org.apache.streampark.console.core.entity.SavePoint;
import org.apache.streampark.console.core.enums.CheckPointType;
import org.apache.streampark.console.core.mapper.SavePointMapper;
import org.apache.streampark.console.core.service.ApplicationService;
import org.apache.streampark.console.core.service.FlinkEnvService;
import org.apache.streampark.console.core.service.SavePointService;
import org.apache.streampark.flink.client.FlinkClient;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.configuration.CheckpointingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true, rollbackFor = Exception.class)
public class SavePointServiceImpl extends ServiceImpl<SavePointMapper, SavePoint>
    implements SavePointService {

  @Autowired private FlinkEnvService flinkEnvService;

  @Autowired private ApplicationService applicationService;

  @Override
  public void expire(Long appId) {
    SavePoint savePoint = new SavePoint();
    savePoint.setLatest(false);
    LambdaQueryWrapper<SavePoint> queryWrapper =
        new LambdaQueryWrapper<SavePoint>().eq(SavePoint::getAppId, appId);
    this.update(savePoint, queryWrapper);
  }

  @Override
  public boolean save(SavePoint entity) {
    this.expire(entity);
    this.expire(entity.getAppId());
    return super.save(entity);
  }

  private void expire(SavePoint entity) {
    FlinkEnv flinkEnv = flinkEnvService.getByAppId(entity.getAppId());
    Application application = applicationService.getById(entity.getAppId());
    Utils.notNull(flinkEnv);
    Utils.notNull(application);

    String numRetainedKey = CheckpointingOptions.MAX_RETAINED_CHECKPOINTS.key();
    String numRetainedFromDynamicProp =
        FlinkClient.extractDynamicPropertiesAsJava(application.getDynamicProperties())
            .get(numRetainedKey);

    int cpThreshold = 0;
    if (numRetainedFromDynamicProp != null) {
      try {
        int value = Integer.parseInt(numRetainedFromDynamicProp.trim());
        if (value > 0) {
          cpThreshold = value;
        } else {
          log.warn(
              "this value of dynamicProperties key: state.checkpoints.num-retained is invalid, must be gt 0");
        }
      } catch (NumberFormatException e) {
        log.warn(
            "this value of dynamicProperties key: state.checkpoints.num-retained invalid, must be number");
      }
    }

    if (cpThreshold == 0) {
      String flinkConfNumRetained = flinkEnv.convertFlinkYamlAsMap().get(numRetainedKey);
      int numRetainedDefaultValue = CheckpointingOptions.MAX_RETAINED_CHECKPOINTS.defaultValue();
      if (flinkConfNumRetained != null) {
        try {
          int value = Integer.parseInt(flinkConfNumRetained.trim());
          if (value > 0) {
            cpThreshold = value;
          } else {
            cpThreshold = numRetainedDefaultValue;
            log.warn(
                "the value of key: state.checkpoints.num-retained in flink-conf.yaml is invalid, must be gt 0, default value: {} will be use",
                numRetainedDefaultValue);
          }
        } catch (NumberFormatException e) {
          cpThreshold = numRetainedDefaultValue;
          log.warn(
              "the value of key: state.checkpoints.num-retained in flink-conf.yaml is invalid, must be number, flink env: {}, default value: {} will be use",
              flinkEnv.getFlinkHome(),
              flinkConfNumRetained);
        }
      } else {
        cpThreshold = numRetainedDefaultValue;
        log.info(
            "the application: {} is not set {} in dynamicProperties or value is invalid, and flink-conf.yaml is the same problem of flink env: {}, default value: {} will be use.",
            application.getJobName(),
            numRetainedKey,
            flinkEnv.getFlinkHome(),
            numRetainedDefaultValue);
      }
    }

    if (CheckPointType.CHECKPOINT.equals(CheckPointType.of(entity.getType()))) {
      cpThreshold = cpThreshold - 1;
    }

    if (cpThreshold == 0) {
      LambdaQueryWrapper<SavePoint> queryWrapper =
          new LambdaQueryWrapper<SavePoint>()
              .eq(SavePoint::getAppId, entity.getAppId())
              .eq(SavePoint::getType, 1);
      this.remove(queryWrapper);
    } else {
      LambdaQueryWrapper<SavePoint> queryWrapper =
          new LambdaQueryWrapper<SavePoint>()
              .select(SavePoint::getTriggerTime)
              .eq(SavePoint::getAppId, entity.getAppId())
              .eq(SavePoint::getType, CheckPointType.CHECKPOINT.get())
              .orderByDesc(SavePoint::getTriggerTime);

      Page<SavePoint> savePointPage =
          this.baseMapper.selectPage(new Page<>(1, cpThreshold + 1), queryWrapper);
      if (!savePointPage.getRecords().isEmpty()
          && savePointPage.getRecords().size() > cpThreshold) {
        SavePoint savePoint = savePointPage.getRecords().get(cpThreshold - 1);
        LambdaQueryWrapper<SavePoint> lambdaQueryWrapper =
            new LambdaQueryWrapper<SavePoint>()
                .eq(SavePoint::getAppId, entity.getAppId())
                .eq(SavePoint::getType, 1)
                .lt(SavePoint::getTriggerTime, savePoint.getTriggerTime());
        this.remove(lambdaQueryWrapper);
      }
    }
  }

  @Override
  public SavePoint getLatest(Long id) {
    LambdaQueryWrapper<SavePoint> queryWrapper =
        new LambdaQueryWrapper<SavePoint>()
            .eq(SavePoint::getAppId, id)
            .eq(SavePoint::getLatest, true);
    return this.getOne(queryWrapper);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public Boolean delete(Long id, Application application) throws InternalException {
    SavePoint savePoint = getById(id);
    try {
      if (CommonUtils.notEmpty(savePoint.getPath())) {
        application.getFsOperator().delete(savePoint.getPath());
      }
      removeById(id);
      return true;
    } catch (Exception e) {
      throw new InternalException(e.getMessage());
    }
  }

  @Override
  public IPage<SavePoint> page(SavePoint savePoint, RestRequest request) {
    Page<SavePoint> page =
        new MybatisPager<SavePoint>().getPage(request, "trigger_time", Constant.ORDER_DESC);
    LambdaQueryWrapper<SavePoint> queryWrapper =
        new LambdaQueryWrapper<SavePoint>().eq(SavePoint::getAppId, savePoint.getAppId());
    return this.page(page, queryWrapper);
  }

  @Override
  public void removeApp(Application application) {
    Long appId = application.getId();

    LambdaQueryWrapper<SavePoint> queryWrapper =
        new LambdaQueryWrapper<SavePoint>().eq(SavePoint::getAppId, appId);
    this.remove(queryWrapper);

    try {
      application
          .getFsOperator()
          .delete(application.getWorkspace().APP_SAVEPOINTS().concat("/").concat(appId.toString()));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
  }
}
