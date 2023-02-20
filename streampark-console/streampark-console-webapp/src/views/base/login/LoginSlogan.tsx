/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Tag } from 'ant-design-vue';
import { defineComponent } from 'vue';
import './LoginSlogan.less';
import Icon from '/@/components/Icon';
import { version } from '../../../../package.json';
export default defineComponent({
  name: 'LoginSlogan',
  setup() {
    return () => {
      return (
        <div className="!text-left w-550px m-auto">
          <div className="mb-5 system_info pt-0">
            <div className="project_title fw-bold text-white mb-3">
              <div
                className="animated-gradient-text_background animated-gradient-text_background-1"
                style={{
                  '--content': '"LanYou"',
                  '--start-color': '#00DFD8',
                  '--end-color': '#FFF',
                }}
              >
                <span className="animated-gradient-text_foreground animated-gradient-text_foreground-1">
                  联友
                </span>
              </div>
              <div className="flex  items-start">
                <div
                  className="animated-gradient-text_background animated-gradient-text_background-2"
                  style={{
                    '--content': '"实时计算"',
                    '--start-color': '#FFF',
                    '--end-color': '#00DFD8',
                  }}
                >
                  <span className="animated-gradient-text_foreground animated-gradient-text_foreground-2">
                    Neptune
                  </span>
                </div>
              </div>
            </div>
            <p className=" text-light-200 leading-40px" style={{ fontSize: '18px' }}>
              <div>让实时数据处理更轻松!</div>
              <div>简洁的实时应用开发框架</div>
              <div>和统一运维监控平台</div>
            </p>
          </div>

          <div className="mt-20px shields z-3 flex items-center">
            <Tag color="#477de9">使用文档</Tag>
            <img
              src="https://img.shields.io/github/stars/apache/incubator-streampark.svg?sanitize=true"
              className="wow fadeInUp"
            ></img>
            <img
              src="https://img.shields.io/github/forks/apache/incubator-streampark.svg?sanitize=true"
              className="wow fadeInUp"
            ></img>
          </div>
        </div>
      );
    };
  },
});
