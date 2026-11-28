package com.macro.mall.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Independent storefront display metadata; source categories and zone relations are read-only. */
@Service
public class MallCategoryCatalog {
    public static final String CONFIG_KEY = "mall.category.display";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public MallCategoryCatalog(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public record Change(Long id, String name, Long parentId, Integer sort, Boolean visible) {}
    public record State(long revision, List<Change> overrides) {}
    public record Entry(long id, long parentId, String name, String icon, int sort, boolean visible,
                        boolean sourceVisible, String sourceName, List<String> zones) {}
    public record Node(long id, long parentId, String name, String icon, List<Node> children) {}
    public record Snapshot(long revision, List<Entry> categories) {}
    private record Source(long id, long parentId, String name, String icon, int sort, boolean visible, Set<String> zones) {}

    private LinkedHashMap<Long,Source> sources(String zone) {
        if(zone!=null && !Set.of("CONSUME","REBUY","PICK").contains(zone)) throw new IllegalArgumentException("未知商品专区");
        var rows=jdbc.queryForList("SELECT c.id,c.parent_id,c.name,c.icon,c.sort,c.show_status,z.zone_code "
                +"FROM pms_product_category c JOIN pms_category_zone z ON z.category_id=c.id WHERE "
                +(zone==null?"z.zone_code='REBUY'":"z.zone_code=?")+" ORDER BY c.sort DESC,c.id",zone==null?new Object[]{}:new Object[]{zone});
        var result=new LinkedHashMap<Long,Source>();
        for(var row:rows) {
            long id=((Number)row.get("id")).longValue();
            Source existing=result.get(id);
            if(existing==null) {
                Object shown=row.get("show_status");
                existing=new Source(id,((Number)row.get("parent_id")).longValue(),String.valueOf(row.get("name")),
                        (String)row.get("icon"),((Number)row.get("sort")).intValue(),
                        Boolean.TRUE.equals(shown) || (shown instanceof Number n && n.intValue()==1),new LinkedHashSet<>());
                result.put(id,existing);
            }
            existing.zones.add(String.valueOf(row.get("zone_code")));
        }
        return result;
    }
    private State readState() {
        return readState(false);
    }
    private State readState(boolean lock) {
        List<String> values=jdbc.queryForList("SELECT config_value FROM sys_config WHERE config_key=?"+(lock?" FOR UPDATE":""),String.class,CONFIG_KEY);
        if(values.isEmpty() || values.get(0)==null) return new State(0,List.of());
        try { return json.readValue(values.get(0),State.class); }
        catch(JsonProcessingException ex) { throw new IllegalStateException("商城分类展示配置无效，请联系管理员",ex); }
    }
    private List<Entry> entries(Map<Long,Source> sources, State state) {
        var overrides=new HashMap<Long,Change>();
        for(Change c:state.overrides()) overrides.put(c.id(),c);
        List<Entry> result=new ArrayList<>();
        for(Source s:sources.values()) {
            Change c=overrides.get(s.id);
            long originalParent=sources.containsKey(s.parentId)?s.parentId:0;
            long parent=c!=null && c.parentId()!=null?c.parentId():originalParent;
            // Deleting an upstream category must not strand its remaining children.
            if(!sources.containsKey(parent))parent=0;
            result.add(new Entry(s.id,parent,c!=null && c.name()!=null?c.name():s.name,s.icon,
                    c!=null && c.sort()!=null?c.sort():s.sort,c==null || !Boolean.FALSE.equals(c.visible()),s.visible,s.name,List.copyOf(s.zones)));
        }
        result.sort(Comparator.comparingInt(Entry::sort).reversed().thenComparingLong(Entry::id));
        return result;
    }
    public Snapshot snapshot() {
        State state=readState();return new Snapshot(state.revision(),entries(sources(null),state));
    }
    public List<Node> tree(String zone) {
        var entries=entries(sources(zone),readState());
        var byId=new HashMap<Long,Entry>();entries.forEach(e->byId.put(e.id(),e));
        Set<Long> visible=new HashSet<>();
        for(Entry e:entries) {
            Entry cursor=e;Set<Long> seen=new HashSet<>();boolean show=true;
            while(cursor!=null) {
                if(!cursor.visible() || !cursor.sourceVisible() || !seen.add(cursor.id())) {show=false;break;}
                cursor=byId.get(cursor.parentId());
            }
            if(show)visible.add(e.id());
        }
        return children(0,entries,visible);
    }
    private List<Node> children(long parent,List<Entry> entries,Set<Long> visible) {
        List<Node> result=new ArrayList<>();
        for(Entry e:entries)if(e.parentId()==parent && visible.contains(e.id()))
            result.add(new Node(e.id(),parent,e.name(),e.icon(),children(e.id(),entries,visible)));
        return result;
    }
    public List<Long> categoryIds(String zone,long selected) {
        var ids=new ArrayList<Long>();
        collect(tree(zone),selected,false,ids);
        if(ids.isEmpty()) return java.util.List.of(-1L); // 分类不可见时返回空结果哨兵，避免搜索接口500
        return ids;
    }
    private void collect(List<Node> nodes,long selected,boolean inside,List<Long> ids) {
        for(Node n:nodes) {
            boolean match=inside || n.id()==selected;
            if(match)ids.add(n.id());
            collect(n.children(),selected,match,ids);
        }
    }
    public List<Long> productIds(String zone) {
        if(zone!=null && !Set.of("CONSUME","REBUY","PICK").contains(zone))throw new IllegalArgumentException("未知商品专区");
        return jdbc.queryForList("SELECT product_id FROM pms_product_zone WHERE "+(zone==null?"zone_code IN ('CONSUME','REBUY')":"zone_code=?"),Long.class,zone==null?new Object[]{}:new Object[]{zone});
    }
    @Transactional(rollbackFor=Exception.class)
    public Snapshot update(long revision,Change change,boolean reset) {
        if(change==null || change.id()==null)throw new IllegalArgumentException("请选择分类");
        var sources=sources(null);Source source=sources.get(change.id());
        if(source==null)throw new IllegalArgumentException("分类不属于消费区或复购区，或已删除");
        jdbc.update("INSERT INTO sys_config(config_key,config_value,config_group,remark) VALUES(?,?,'mall_catalog','全商城分类独立展示') ON DUPLICATE KEY UPDATE config_key=config_key",CONFIG_KEY,"{\"revision\":0,\"overrides\":[]}");
        // A locking read must read the revision itself: MySQL REPEATABLE READ may retain an older snapshot.
        State current=readState(true);
        if(current.revision()!=revision)throw new IllegalArgumentException("分类展示已被其他管理员更新，请刷新后重试");
        Map<Long,Change> changes=new LinkedHashMap<>();current.overrides().forEach(c->changes.put(c.id(),c));
        if(reset)changes.remove(change.id());
        else {
            String name=change.name()==null?null:change.name().trim();
            if(name!=null && (name.isEmpty() || name.length()>80 || name.chars().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("展示名称须为1至80个字符");
            if(change.sort()!=null && Math.abs((long)change.sort())>1000000)throw new IllegalArgumentException("排序值超出范围");
            if(change.parentId()!=null && change.parentId()!=0 && !sources.containsKey(change.parentId()))throw new IllegalArgumentException("上级分类不在汇总范围内");
            long originalParent=sources.containsKey(source.parentId)?source.parentId:0;
            changes.put(change.id(),new Change(change.id(),Objects.equals(name,source.name)?null:name,
                    change.parentId()!=null && change.parentId()==originalParent?null:change.parentId(),
                    change.sort()!=null && change.sort()==source.sort?null:change.sort(),Boolean.FALSE.equals(change.visible())?false:null));
        }
        State next=new State(Math.addExact(current.revision(),1),new ArrayList<>(changes.values()));
        List<Entry> effective=entries(sources,next);Map<Long,Entry> byId=new HashMap<>();effective.forEach(e->byId.put(e.id(),e));
        for(Entry entry:effective) {
            Set<Long> seen=new HashSet<>();Entry cursor=entry;
            while(cursor!=null) {
                if(!seen.add(cursor.id()))throw new IllegalArgumentException("上级分类不能是自身或其下级分类");
                if(seen.size()>8)throw new IllegalArgumentException("展示分类最多支持8级");
                cursor=byId.get(cursor.parentId());
            }
        }
        try { jdbc.update("UPDATE sys_config SET config_value=? WHERE config_key=?",json.writeValueAsString(next),CONFIG_KEY); }
        catch(JsonProcessingException ex) { throw new IllegalStateException(ex); }
        return new Snapshot(next.revision(),effective);
    }
}
