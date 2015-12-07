/*
 * Copyright 2008-2009 the original 赵永春(zyc@hasor.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.hasor.rsf.rpc;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.more.RepeateException;
import org.more.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.hasor.core.Provider;
import net.hasor.rsf.RsfBindInfo;
import net.hasor.rsf.RsfBinder;
import net.hasor.rsf.RsfService;
import net.hasor.rsf.RsfSettings;
import net.hasor.rsf.container.RsfBindBuilder;
import net.hasor.rsf.container.ServiceInfo;
import net.hasor.rsf.rpc.context.AbstractRsfContext;
import net.hasor.rsf.rpc.event.Events;
import net.hasor.rsf.utils.RsfRuntimeUtils;
/**
 * 本地服务注册中心
 * @version : 2014年11月30日
 * @author 赵永春(zyc@hasor.net)
 */
public class RsfBindCenter implements BindCenter {
    protected Logger                                      logger = LoggerFactory.getLogger(getClass());
    /* Group -> Name -> Version*/
    private final ConcurrentMap<String, ServiceInfo<?>> rsfServiceMap;
    private final ConcurrentMap<String, Provider<?>>      providerMap;
    private final AbstractRsfContext                      rsfContext;
    //
    public RsfBindCenter(AbstractRsfContext rsfContext) {
        logger.info("create RsfBindCenter.");
        this.rsfContext = rsfContext;
        this.rsfServiceMap = new ConcurrentHashMap<String, ServiceInfo<?>>();
        this.providerMap = new ConcurrentHashMap<String, Provider<?>>();
    }
    //
    public RsfBinder getRsfBinder() {
        return new RsfBindBuilder(this.rsfContext);
    }
    //
    public <T> ServiceInfo<T> getService(String serviceID) {
        return (ServiceInfo<T>) this.rsfServiceMap.get(serviceID);
    }
    //
    public <T> ServiceInfo<T> getService(Class<T> serviceType) {
        RsfSettings rsfSettings = this.rsfContext.getSettings();
        String serviceGroup = rsfSettings.getDefaultGroup();
        String serviceName = serviceType.getName();
        String serviceVersion = rsfSettings.getDefaultVersion();
        //覆盖
        RsfService serviceInfo = serviceType.getAnnotation(RsfService.class);
        if (serviceInfo != null) {
            if (StringUtils.isBlank(serviceInfo.group()) == false) {
                serviceGroup = serviceInfo.group();
            }
            if (StringUtils.isBlank(serviceInfo.name()) == false) {
                serviceName = serviceInfo.name();
            }
            if (StringUtils.isBlank(serviceInfo.version()) == false) {
                serviceVersion = serviceInfo.version();
            }
        }
        return getService(serviceGroup, serviceName, serviceVersion);
    }
    //
    public <T> ServiceInfo<T> getServiceByName(String serviceName) {
        RsfSettings rsfSettings = this.rsfContext.getSettings();
        return getService(rsfSettings.getDefaultGroup(), serviceName, rsfSettings.getDefaultVersion());
    }
    //
    public <T> ServiceInfo<T> getService(String group, String name, String version) {
        String serviceID = RsfRuntimeUtils.bindID(group, name, version);
        return (ServiceInfo<T>) this.rsfServiceMap.get(serviceID);
    }
    //
    /**获取已经注册的所有服务名称。*/
    public List<String> getServiceIDs() {
        return new ArrayList<String>(this.rsfServiceMap.keySet());
    }
    //
    /**回收已经发布的服务*/
    public void recoverService(RsfBindInfo<?> bindInfo) {
        this.rsfContext.getEventContext().fireSyncEvent(Events.UnService, bindInfo);
        String serviceID = bindInfo.getBindID();
        this.rsfServiceMap.remove(serviceID);
    }
    /**发布服务*/
    public void publishService(ServiceInfo<?> bindInfo, Provider<?> provider) {
        String serviceID = bindInfo.getBindID();
        if (this.rsfServiceMap.containsKey(serviceID) == true) {
            throw new RepeateException("Repeate:" + serviceID); /*重复检查*/
        }
        ServiceInfo<?> serviceDefine = this.rsfServiceMap.putIfAbsent(serviceID, bindInfo);
        if (serviceDefine != null) {
            throw new RepeateException("Repeate:" + serviceID); /*重复检查*/
        }
        //
        String eventName = null;
        if (provider != null) {
            this.providerMap.put(serviceID, provider);
            eventName = Events.ServiceProvider;
        } else {
            eventName = Events.ServiceCustomer;
        }
        this.rsfContext.getEventContext().fireSyncEvent(eventName, bindInfo);
    }
    /**获取服务对象*/
    public <T> Provider<T> getProvider(RsfBindInfo<T> bindInfo) {
        return (Provider<T>) this.providerMap.get(bindInfo.getBindID());
    }
    @Override
    public void updateDefaultRoute(String flowControl) throws IOException {
        this.rsfContext.getAddressPool().refreshDefaultFlowControl(flowControl);
    }
    @Override
    public void updateRoute(String serviceID, String flowControl) throws IOException {
        this.rsfContext.getAddressPool().refreshFlowControl(flowControl, serviceID);
    }
    @Override
    public void updateAddress(String serviceID, Collection<URI> newHostList) {
        ServiceInfo<?> define = this.getService(serviceID);
        if (define != null) {
            this.rsfContext.getAddressPool().updateAddress(define.getBindID(), newHostList);
        }
    }
}