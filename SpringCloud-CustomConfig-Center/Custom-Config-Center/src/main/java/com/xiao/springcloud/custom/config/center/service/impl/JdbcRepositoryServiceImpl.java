package com.xiao.springcloud.custom.config.center.service.impl;

import com.xiao.springcloud.custom.config.center.service.RepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * [简要描述]: 资源获取，jdbc实现
 * [详细描述]:
 *
 * @author llxiao
 * @version 1.0, 2018/11/23 08:39
 * @since JDK 1.8
 */
@Slf4j
@Service
public class JdbcRepositoryServiceImpl implements RepositoryService
{
    //区域查询
    private static final String REGION_SQL = "select region_id from t_server_host_config h where h.server_host = ?";

    //主配置查询
    private static final String ITEM_GROUP_SQL =
            "select air.item_group_id from t_application_config ac STRAIGHT_JOIN t_application_item_group_relation air on ac.id = air.application_id where ac.region_id = ("
                    + REGION_SQL + ") and ac.application = ? and ac.label = ? and ac.profile = ?";

    // 配置组查询配置项
    private static final String ITEM_ID_SQL = "select cigr.item_id from t_config_item_group cig left join t_config_item_group_relation cigr on cig.id = cigr.group_id where cig.id in (:ids)";

    // 具体配置项内容查询
    private static final String ITEM_SQL = "select ci.item_key,ci.item_value from t_config_item ci where ci.status = 0 and ci.id in (:ids)";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //返回结果获取long id结合
    private ResultSetExtractor<List<Long>> tResultSetExtractor = rs ->
    {
        List<Long> itemGroups = new ArrayList<>();
        while (rs.next())
        {
            itemGroups.add(rs.getLong(1));
        }
        return itemGroups;
    };

    //获取具体的资源集合
    private ResultSetExtractor<Map<String, Object>> sourceExtractor = rs ->
    {
        Map<String, Object> map = new LinkedHashMap<>();
        while (rs.next())
        {
            map.put(rs.getString(1), rs.getObject(2));
        }
        return map;
    };

    /**
     * [简要描述]:<br/>
     * [详细描述]:<br/>
     *
     * @param ip : 服务IP
     * @param application : 应用名称
     * @param label : 标签默认master
     * @param profile : 环境
     * @return java.util.Map
     * llxiao  2018/11/23 - 8:38
     **/
    @Override
    public Map<String, Object> getPropertySource(String ip, String application, String label, String profile)
    {
        if (log.isDebugEnabled())
        {
            log.debug("Query config properties for Jdbc!");
            log.debug("[ip:{},application:{},label:{},profile:{}]", ip, application, label, profile);
        }
        Map<String, Object> map = new HashMap<>();
        List<Long> itemGroupIds = queryItemGroups(ip, application, label, profile);
        if (!CollectionUtils.isEmpty(itemGroupIds))
        {
            List<Long> itemIds = queryItemsForGroups(itemGroupIds);
            if (!CollectionUtils.isEmpty(itemIds))
            {
                map = queryItems(itemIds);
                if (log.isDebugEnabled())
                {
                    log.debug("Found properties:{}", map);
                }
            }
            else
            {
                log.warn("Current application environment has no configuration information yet!");
            }
        }
        else
        {
            log.warn("Environmental configuration information for the corresponding region was not found!");
        }
        return map;
    }

    //获取资源集合
    private Map<String, Object> queryItems(List<Long> itemIds)
    {
        NamedParameterJdbcTemplate itemsTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        Map<String, List<Long>> stringMap = new HashMap<>();
        stringMap.put("ids", itemIds);
        return itemsTemplate.query(ITEM_SQL, stringMap, sourceExtractor);
    }

    // 获取所有的Item 的ID集合
    private List<Long> queryItemsForGroups(List<Long> itemGroupIds)
    {
        NamedParameterJdbcTemplate itemIdsTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        Map<String, List<Long>> stringMap = new HashMap<>();
        stringMap.put("ids", itemGroupIds);
        return itemIdsTemplate.queryForList(ITEM_ID_SQL, stringMap, Long.class);
    }

    //获取对应的配置项组列表
    private List<Long> queryItemGroups(String ip, String application, String label, String profile)
    {
        return jdbcTemplate
                .query(ITEM_GROUP_SQL, new Object[] { ip, application, label, profile }, tResultSetExtractor);
    }
}
