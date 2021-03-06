package iquantex.com.dolphinscheduler.command.impl;

import com.alibaba.fastjson.JSONObject;
import iquantex.com.dolphinscheduler.command.Authenticator;
import iquantex.com.dolphinscheduler.command.Constant;
import iquantex.com.dolphinscheduler.pojo.Result;
import iquantex.com.entity.SheetEnv;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mujp
 */
public class AuthenticatorImpl implements Authenticator {
    protected static final Log logger = LogFactory.getLog(AuthenticatorImpl.class);
    @Override
    public Result authenticate(SheetEnv login) {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        CloseableHttpResponse response = null;
        Result result = new Result();
        result.setUserName(login.getUserName());
        result.setProjectName(login.getProjectName());
        String content = null;
        try {
            List<NameValuePair> parameters = new ArrayList<>();
            parameters.add(new BasicNameValuePair("userName", login.getUserName()));
            parameters.add(new BasicNameValuePair("userPassword", login.getPassword()));
            URI uri = new URIBuilder(Constant.URL_HEADER+login.getIp()+":"+login.getPort()+"/dolphinscheduler/login")
                    .setParameters(parameters)
                    .build();
            HttpPost httpGet = new HttpPost(uri);
            response = httpclient.execute(httpGet);
            logger.info("【getLoginToken接口】返回状态码：" + response.getStatusLine().getStatusCode());
            content = EntityUtils.toString(response.getEntity(), "UTF-8");
            logger.info("【getLoginToken接口】返回结果为: " + content);
            String data = JSONObject.parseObject(content).getString("data");
            if (data != null ){
                result.setData(JSONObject.parseObject(data).getString("sessionId"));
            }
            result.setState(Constant.STATE_SUCCESS);
        } catch (ClientProtocolException e) {
            logger.error("【getLoginToken接口】客户端连接异常：" + e);
            result.setMsg("【getLoginToken接口】客户端连接异常：" + e);
        } catch (IOException e) {
            logger.error("【getLoginToken接口】客户端IO异常：" + e);
            result.setMsg("【getLoginToken接口】客户端IO异常：" + e);
        } catch (URISyntaxException e) {
            logger.error("【getLoginToken接口】客户端URI构建异常：" + e);
            result.setMsg("【getLoginToken接口】客户端URI构建异常：" + e);
        } catch (Exception e) {
            logger.error("【getLoginToken接口】异常：" + e);
            result.setMsg("【getLoginToken接口】异常：" + e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    logger.error("【getLoginToken接口】关闭response响应异常：" + e);
                }
            }
            try {
                httpclient.close();
            } catch (IOException e) {
                logger.error("【getLoginToken接口】关闭客户端异常：" + e);
            }
        }
        return result;
    }
}
