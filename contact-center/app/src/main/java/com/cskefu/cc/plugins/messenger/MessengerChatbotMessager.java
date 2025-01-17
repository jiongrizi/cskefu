/*
 * Copyright (C) 2023 Beijing Huaxia Chunsong Technology Co., Ltd.
 * <https://www.chatopera.com>, Licensed under the Chunsong Public
 * License, Version 1.0  (the "License"), https://docs.cskefu.com/licenses/v1.html
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cskefu.cc.plugins.messenger;

import com.alibaba.fastjson.JSONObject;
import com.cskefu.cc.basic.MainContext;
import com.cskefu.cc.model.FbMessenger;
import com.cskefu.cc.model.PassportWebIMUser;
import com.cskefu.cc.persistence.repository.FbMessengerRepository;
import com.cskefu.cc.persistence.repository.PassportWebIMUserRepository;
import com.cskefu.cc.plugins.chatbot.ChatbotConstants;
import com.cskefu.cc.plugins.chatbot.ChatbotContext;
import com.cskefu.cc.socketio.message.ChatMessage;
import com.chatopera.compose4j.Functional;
import com.chatopera.compose4j.Middleware;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MessengerChatbotMessager implements Middleware<ChatbotContext> {
    private final static Logger logger = LoggerFactory.getLogger(MessengerChatbotMessager.class);

    private final static String EVALUATION_YES_REPLY = "evaluationYesReply";
    private final static String EVALUATION_NO_REPLY = "evaluationNoReply";

    @Autowired
    private MessengerMessageProxy messengerMessageProxy;

    @Autowired
    private PassportWebIMUserRepository onlineUserRes;

    @Autowired
    private FbMessengerRepository fbMessengerRepository;

    private final Map<String, String> messengerConfig = new HashMap<String, String>() {
        {
            put("transferManualService", "转人工");
            put("suggestQuestion", "您是否想问以下问题");
            put("evaluationAsk", "以上答案是否对您有帮助");
            put("evaluationYes", "是");
            put("evaluationNo", "否");
            put(EVALUATION_YES_REPLY, "感谢您的反馈，我们会做的更好！");
            put(EVALUATION_NO_REPLY, "感谢您的反馈，机器人在不断的学习！");
        }
    };

    @Override
    public void apply(final ChatbotContext ctx, final Functional next) {
        ChatMessage resp = ctx.getResp();
        if (MainContext.ChannelType.MESSENGER.toString().equals(resp.getChannel())) {

            final PassportWebIMUser passportWebIMUser = onlineUserRes.findOneByUserid(
                    resp.getUserid());

            Map<String, String> configMap = messengerConfig;
            FbMessenger fbMessenger = fbMessengerRepository.findOneByPageId(passportWebIMUser.getAppid());
            if (fbMessenger != null && StringUtils.isNotBlank(fbMessenger.getConfig())) {
                configMap = (Map<String, String>) JSONObject.parse(fbMessenger.getConfig());
            }

            if (StringUtils.isNotBlank(resp.getExpmsg())) {
                String jsonStr = processGenericTemplate(resp.getExpmsg(), configMap);
                messengerMessageProxy.send(passportWebIMUser.getAppid(), passportWebIMUser.getUserid(), JSONObject.parseObject(jsonStr), fbMessenger);
            } else {
                messengerMessageProxy.send(passportWebIMUser.getAppid(), passportWebIMUser.getUserid(), processTextTemplate(resp.getMessage(), configMap));
            }
        }
        next.apply();
    }

    /**
     * 替换文本消息
     *
     * @param template
     * @param params
     * @return
     */
    public static String processTextTemplate(String template, Map<String, String> params) {
        if (StringUtils.equals(template, ChatbotConstants.PROVIDER_FEEDBACK_EVAL_POSITIVE_REPLY_PLACEHOLDER)) {
            if (params.containsKey(EVALUATION_YES_REPLY) && StringUtils.isNotBlank(params.get(EVALUATION_YES_REPLY))) {
                return params.get(EVALUATION_YES_REPLY);
            }
        } else if (StringUtils.equals(template, ChatbotConstants.PROVIDER_FEEDBACK_EVAL_NEGATIVE_REPLY_PLACEHOLDER)) {
            if (params.containsKey(EVALUATION_NO_REPLY) && StringUtils.isNotBlank(params.get(EVALUATION_NO_REPLY))) {
                return params.get(EVALUATION_NO_REPLY);
            }
        } else if (StringUtils.equals(template, "${leaveMeAlone}")) {
            return "";
        }
        return template;
    }

    /**
     * 替换模版消息
     *
     * @param template
     * @param params
     * @return
     */
    public static String processGenericTemplate(String template, Map<String, String> params) {
        Matcher m = Pattern.compile("\\$\\{\\w+\\}").matcher(template);

        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String param = m.group();
            Object value = params.get(param.substring(2, param.length() - 1));
            m.appendReplacement(sb, value == null ? "" : value.toString());
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
