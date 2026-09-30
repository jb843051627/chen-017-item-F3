package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.ISqlSegment;
import com.fc.v2.mapper.auto.TItemCatalogRowMapper;
import com.fc.v2.mapper.auto.TItemCatalogVerMapper;
import com.fc.v2.mapper.auto.TItemChangeBillMapper;
import com.fc.v2.mapper.auto.TItemChangeSignMapper;
import com.fc.v2.mapper.auto.TItemDeptArchMapper;
import com.fc.v2.model.auto.TItemCatalogRow;
import com.fc.v2.model.auto.TItemCatalogVer;
import com.fc.v2.model.auto.TItemChangeBill;
import com.fc.v2.model.auto.TItemChangeSign;
import com.fc.v2.model.auto.TItemDeptArch;
import com.fc.v2.service.impl.TItemChangeBillServiceImpl;

/**
 * 三道口核签口径单元测试：不起 Spring、不落库，mapper 用 Mockito 桩接内存表。
 */
class TItemChangeBillServiceImplTest {

    private static final int ROLE_ELEMENTS = 1;
    private static final int ROLE_REGISTRY = 2;

    private TItemChangeBillServiceImpl service;

    private FakeTable<TItemChangeBill> bills;
    private FakeTable<TItemChangeSign> signs;
    private FakeTable<TItemDeptArch> archs;
    private FakeTable<TItemCatalogRow> rows;
    private FakeTable<TItemCatalogVer> vers;

    @BeforeEach
    void setUp() {
        service = new TItemChangeBillServiceImpl();

        TItemChangeBillMapper billMapper = mock(TItemChangeBillMapper.class);
        TItemChangeSignMapper signMapper = mock(TItemChangeSignMapper.class);
        TItemDeptArchMapper archMapper = mock(TItemDeptArchMapper.class);
        TItemCatalogRowMapper rowMapper = mock(TItemCatalogRowMapper.class);
        TItemCatalogVerMapper verMapper = mock(TItemCatalogVerMapper.class);

        bills = new FakeTable<>();
        signs = new FakeTable<>();
        archs = new FakeTable<>();
        rows = new FakeTable<>();
        vers = new FakeTable<>();

        wire(billMapper, bills);
        wire(signMapper, signs);
        wire(archMapper, archs);
        wire(rowMapper, rows);
        wire(verMapper, vers);
        when(signMapper.update(any(), any(Wrapper.class))).thenAnswer(inv ->
                signs.updateByWrapper(inv.getArgument(1)));

        ReflectionTestUtils.setField(service, "itemChangeBillMapper", billMapper);
        ReflectionTestUtils.setField(service, "itemChangeSignMapper", signMapper);
        ReflectionTestUtils.setField(service, "itemDeptArchMapper", archMapper);
        ReflectionTestUtils.setField(service, "itemCatalogRowMapper", rowMapper);
        ReflectionTestUtils.setField(service, "itemCatalogVerMapper", verMapper);

        // 一条在编承办底册（前置检过）
        TItemDeptArch arch = new TItemDeptArch();
        arch.setSiteNo("ZW00");
        arch.setStatus(0);
        arch.setDelFlag(0);
        archs.insert(arch);
    }

    private <T> void wire(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, FakeTable<T> table) {
        when(mapper.selectById(any(Serializable.class))).thenAnswer(inv ->
                table.get(((Number) inv.getArgument(0)).longValue()));
        when(mapper.insert(any())).thenAnswer(inv -> table.insert(inv.getArgument(0)));
        when(mapper.updateById(any())).thenAnswer(inv -> table.updateById(inv.getArgument(0)));
        when(mapper.selectCount(any(Wrapper.class))).thenAnswer(inv ->
                Integer.valueOf(table.count(inv.getArgument(0))));
        when(mapper.selectList(any(Wrapper.class))).thenAnswer(inv ->
                table.selectList(inv.getArgument(0)));
        when(mapper.selectOne(any(Wrapper.class))).thenAnswer(inv ->
                table.selectOne(inv.getArgument(0)));
    }

    // ---------- 县核起件 ----------

    @Test
    void create_missingAnyOfFour_fails() {
        for (int missing = 0; missing < 4; missing++) {
            TItemChangeBill d = draft();
            if (missing == 0) d.setItemCode(null);
            if (missing == 1) d.setBeforeText(null);
            if (missing == 2) d.setAfterText(null);
            if (missing == 3) d.setChangeReason(null);
            assertNull(service.createBill(d), "第" + missing + "样缺了件起不来");
        }
        assertEquals(0, bills.all().size());
    }

    @Test
    void create_archMissingOrDismissed_fails() {
        TItemChangeBill d1 = draft();
        d1.setSiteNo(null);
        assertNull(service.createBill(d1));

        TItemChangeBill d2 = draft();
        d2.setSiteNo("ZW-MISSING");
        assertNull(service.createBill(d2));

        TItemDeptArch gone = new TItemDeptArch();
        gone.setSiteNo("ZW01");
        gone.setStatus(1);
        gone.setDelFlag(0);
        archs.insert(gone);
        TItemChangeBill d3 = draft();
        d3.setSiteNo("ZW01");
        assertNull(service.createBill(d3), "已撤并底册过不了前置检，件停在县核");
    }

    @Test
    void create_ok_startsAtCounty() {
        TItemChangeBill saved = service.createBill(draft());
        assertNotNull(saved);
        assertEquals(0, saved.getNodeNo().intValue());
        assertEquals(1, saved.getRoundNo().intValue());
        assertEquals(0, saved.getStatus().intValue());
        assertEquals(0, saved.getSignCount().intValue());
        assertEquals(1, saved.getNeedCount().intValue());
    }

    @Test
    void create_secondBillSameItemSameYear_flaggedOnSpot() {
        TItemChangeBill first = service.createBill(draft());
        assertNotNull(first);
        // 单测无 MetaObjectHandler，补盖创建时间（运行时由填充器自动盖）
        first.setCreateTime(new java.util.Date());
        bills.updateById(first);

        TItemChangeBill second = service.createBill(draft());
        assertNotNull(second);
        assertTrue(second.getRemark().contains("当年第2件"),
                "同一项事项当年第二件要报的，当场点名");
    }

    // ---------- 按口次推进 ----------

    @Test
    void countySign_advancesToCity() {
        Long id = service.createBill(draft()).getId();
        TItemChangeBill r = service.approve(id, "县办事员甲", "材料齐");
        assertEquals(1, r.getNodeNo().intValue());
        assertEquals(0, r.getSignCount().intValue(), "过口后新口从0数");
    }

    @Test
    void citySign_onlyTakesItemCode_notNames() {
        Long id = service.createBill(draft()).getId();
        service.approve(id, "县办事员甲", null);

        // 省册里没有这个事项码：名号撞得一个不差也不顶用，市核不接
        TItemChangeBill blocked = service.approve(id, "两位办事人姓名一个不差", null);
        assertNull(blocked);
        assertEquals(1, bills.get(id).getNodeNo().intValue(), "件还停在市核");

        // 省下发册里挂上同一事项码后，市核比对码即过
        seedListedItem("XZQR-001");
        TItemChangeBill r = service.approve(id, "市对照员乙", null);
        assertEquals(2, r.getNodeNo().intValue());
        assertEquals(1, r.getSignMode().intValue(), "省定是两名点齐之口");
        assertEquals(2, r.getNeedCount().intValue());
    }

    @Test
    void province_requiresTwoRoleSignatures_oneStuck() {
        seedListedItem("XZQR-001");
        Long id = reachProvince();

        // 无分工的旧签法到省定不接
        assertNull(service.approve(id, "省员甲", "我一个人说了算"));

        TItemChangeBill once = service.approve(id, "省员甲", ROLE_ELEMENTS, "要素齐");
        assertEquals(2, once.getNodeNo().intValue());
        assertEquals(1, once.getSignCount().intValue());
        assertEquals(0, once.getStatus().intValue(), "只到一名，件仍卡省定，见不着结案");
        assertEquals(1, service.countSigns(id), "落名数顺签迹点，不看屏上的框");

        // 同一种分工再来一笔不作数
        assertNull(service.approve(id, "省员丙", ROLE_ELEMENTS, "再盯一遍要素"));
        // 同一人想把另一笔也包了——没有代笔的口子
        assertNull(service.approve(id, "省员甲", ROLE_REGISTRY, "名录我也盯"));
    }

    @Test
    void province_twoSignaturesSameMinute_stillTwo_sealsAndSyncs() {
        seedListedItem("XZQR-001");
        Long id = reachProvince();

        service.approve(id, "省员甲", ROLE_ELEMENTS, "要素齐");
        TItemChangeBill sealed = service.approve(id, "省员乙", ROLE_REGISTRY, "合规矩");
        // 掐同一分钟落笔也按两笔算
        assertEquals(2, service.countSigns(id));
        assertEquals(5, sealed.getStatus().intValue(), "省定落章，全单封住");

        // 封单之后谁也不能再动，要动另开一张单
        assertNull(service.approve(id, "省员甲", ROLE_ELEMENTS, "补一笔"));
        assertNull(service.reject(id, "省员甲", ROLE_REGISTRY, "想推翻"));
        assertNull(service.withdraw(id, "县办事员甲", "想撤"));

        // 对外名录跟着走这一道，且与档里同一回点数（县1+市1+省2=4）
        List<TItemCatalogVer> published = vers.all();
        assertEquals(1, published.size());
        assertEquals(3, published.get(0).getStage().intValue(), "已上柜");
        assertTrue(published.get(0).getLastAction().endsWith(":4"),
                "对外与档里由同一回点数交出");
    }

    // ---------- 止步与封住 ----------

    @Test
    void reject_stopsAtNode_noAppendNoRewrite() {
        seedListedItem("XZQR-001");
        Long id = reachProvince();
        TItemChangeBill r = service.reject(id, "省员甲", "不予通过");
        assertEquals(3, r.getStatus().intValue());
        assertEquals(2, r.getNodeNo().intValue(), "停在省定该口");

        assertNull(service.approve(id, "省员乙", ROLE_REGISTRY, "再补一笔"), "既不追加");
        TItemChangeBill again = service.selectTItemChangeBillById(id);
        assertEquals(3, again.getStatus().intValue(), "也不改写");
    }

    @Test
    void withdraw_stopsAtCurrentNode() {
        Long id = service.createBill(draft()).getId();
        service.approve(id, "县办事员甲", null);
        TItemChangeBill r = service.withdraw(id, "承办人", "撤回报送");
        assertEquals(4, r.getStatus().intValue());
        assertEquals(1, r.getNodeNo().intValue());
        assertNull(service.approve(id, "市对照员乙", null));
    }

    @Test
    void roleReject_onlyAtProvince_sinkAndRestartFromCounty() {
        seedListedItem("XZQR-001");
        Long id = service.createBill(draft()).getId();
        // 带分工的“表述不合规矩打回”不是县核/市核的动作
        assertNull(service.reject(id, "县办事员甲", ROLE_REGISTRY, "想整批沉底"));
        service.approve(id, "县办事员甲", null);

        // 一件走到落章封住，封单后不能打回
        service.approve(id, "市对照员乙", null);
        service.approve(id, "省员甲", ROLE_ELEMENTS, null);
        service.approve(id, "省员乙", ROLE_REGISTRY, null);
        assertNull(service.reject(id, "省复核", ROLE_REGISTRY, "迟了"));

        // 另起一件走到省定、只有一笔时打回
        Long id2 = reachProvince();
        service.approve(id2, "省员甲", ROLE_ELEMENTS, null);
        TItemChangeBill back = service.reject(id2, "省员乙", ROLE_REGISTRY, "表述不合规矩");
        assertEquals(2, back.getRoundNo().intValue(), "新一轮");
        assertEquals(0, back.getNodeNo().intValue(), "从县核重新一层层往上攒");
        assertEquals(0, back.getStatus().intValue());
        assertEquals(0, back.getSignCount().intValue());
        // 先前各口落过的名目整批沉底（置删不物理删），与新批不并排；只点本单
        int sunk = 0;
        for (TItemChangeSign s : signs.all()) {
            if (id2.equals(s.getBillId())) {
                assertEquals(1, s.getDelFlag().intValue());
                sunk++;
            }
        }
        assertEquals(3, sunk);

        // 重新走一遍：县 → 市 → 省两笔，落章
        service.approve(id2, "县办事员甲", null);
        service.approve(id2, "市对照员乙", null);
        service.approve(id2, "省员甲", ROLE_ELEMENTS, null);
        service.approve(id2, "省员乙", ROLE_REGISTRY, null);
        TItemChangeBill sealed = service.selectTItemChangeBillById(id2);
        assertEquals(5, sealed.getStatus().intValue());
        assertEquals(2, sealed.getRoundNo().intValue());
        // 沉下去3笔 + 新补4笔，点数只点新批
        assertEquals(7, countSignsOnBill(id2));
        assertEquals(4, countSignsOnRound(id2, 2));
        assertTrue(service.verify(id2), "新一轮仍经得起倒数复核");
    }

    @Test
    void legacyRollback_keepsOldShape() {
        seedListedItem("XZQR-001");
        Long id = reachProvince();
        TItemChangeBill r = service.rollback(id, "退回");
        assertEquals(1, r.getNodeNo().intValue(), "旧法入参与返回不改");
    }

    // ---------- 倒数复核 ----------

    @Test
    void verify_sealedRoundTrip_ok() {
        seedListedItem("XZQR-001");
        Long id = reachProvince();
        assertFalse(service.verify(id), "未结案经不起复核");

        service.approve(id, "省员甲", ROLE_ELEMENTS, null);
        service.approve(id, "省员乙", ROLE_REGISTRY, null);
        assertTrue(service.verify(id), "从省定倒数回县核，名目一笔不差");
    }

    @Test
    void verify_wrongPublishedContentOrCount_fails() {
        seedListedItem("XZQR-001");
        Long id = reachProvince();
        service.approve(id, "省员甲", ROLE_ELEMENTS, null);
        service.approve(id, "省员乙", ROLE_REGISTRY, null);
        assertTrue(service.verify(id));

        TItemCatalogVer pub = vers.all().get(0);
        // 对外那一页与改定版要素对不上号
        pub.setContent("XZQR-001|被人偷换的一版");
        vers.updateById(pub);
        assertFalse(service.verify(id));

        pub.setContent("XZQR-001|" + draft().getAfterText());
        pub.setLastAction("changeBill:" + id + ":99");
        vers.updateById(pub);
        assertFalse(service.verify(id), "对外几名与档里几名合不拢，这口便算没齐");
    }

    // ---------- helpers ----------

    private Long reachProvince() {
        Long id = service.createBill(draft()).getId();
        service.approve(id, "县办事员甲", null);
        service.approve(id, "市对照员乙", null);
        return id;
    }

    private void seedListedItem(String code) {
        TItemCatalogRow row = new TItemCatalogRow();
        row.setItemCode(code);
        row.setDelFlag(0);
        rows.insert(row);
    }

    private TItemChangeBill draft() {
        TItemChangeBill d = new TItemChangeBill();
        d.setBillNo("BG-2026-0001");
        d.setItemCode("XZQR-001");
        d.setBeforeText("旧话：承办口径旧版");
        d.setAfterText("新话：承办口径改后版");
        d.setChangeReason("去年八月补录行政确认承办口径");
        d.setSiteNo("ZW00");
        return d;
    }

    /** 测试用：直接从签迹点某一轮全部活口名数（不走单据字段）。 */
    private int countSignsOnRound(Long billId, int round) {
        int n = 0;
        for (TItemChangeSign s : signs.all()) {
            if (billId.equals(s.getBillId()) && s.getRoundNo() == round && s.getDelFlag() == 0) {
                n++;
            }
        }
        return n;
    }

    /** 测试用：点某一张单全部签迹（含沉底批）。 */
    private int countSignsOnBill(Long billId) {
        int n = 0;
        for (TItemChangeSign s : signs.all()) {
            if (billId.equals(s.getBillId())) {
                n++;
            }
        }
        return n;
    }

    // ==================================================================
    // 内存表：实现 mapper 桩所需的少量方法，Wrapper 只解析服务用到的
    // eq/ge/orderBy/set。
    // ==================================================================

    static class FakeTable<T> {
        private final Map<Long, T> rows = new LinkedHashMap<>();
        private final AtomicLong seq = new AtomicLong(1000);

        T get(Long id) {
            return rows.get(id);
        }

        List<T> all() {
            return new ArrayList<>(rows.values());
        }

        @SuppressWarnings("unchecked")
        private Long readId(T t) {
            Object v = ReflectionTestUtils.getField(t, "id");
            return v == null ? null : ((Number) v).longValue();
        }

        int insert(T entity) {
            Long id = readId(entity);
            if (id == null) {
                id = seq.incrementAndGet();
                ReflectionTestUtils.setField(entity, "id", id);
            }
            rows.put(id, entity);
            return 1;
        }

        int updateById(T entity) {
            Long id = readId(entity);
            if (id == null || !rows.containsKey(id)) {
                return 0;
            }
            rows.put(id, entity);
            return 1;
        }

        int count(Wrapper<T> w) {
            return selectList(w).size();
        }

        List<T> selectList(Wrapper<T> w) {
            List<T> matched = new ArrayList<>();
            for (T t : rows.values()) {
                if (matches(t, w)) {
                    matched.add(t);
                }
            }
            List<String> orderKeys = parseOrder(w);
            if (!orderKeys.isEmpty()) {
                String col = orderKeys.get(0);
                boolean asc = orderKeys.get(1).equals("ASC");
                Comparator<T> byKey = (a, b) -> {
                    @SuppressWarnings("unchecked")
                    Comparable<Object> va = (Comparable<Object>) ReflectionTestUtils.getField(a, snakeToCamel(col));
                    Object vb = ReflectionTestUtils.getField(b, snakeToCamel(col));
                    if (va == null && vb == null) return 0;
                    if (va == null) return asc ? -1 : 1;
                    if (vb == null) return asc ? 1 : -1;
                    return asc ? va.compareTo(vb) : -va.compareTo(vb);
                };
                matched.sort(byKey);
            } else {
                matched.sort(Comparator.comparingLong(this::readId));
            }
            return matched;
        }

        T selectOne(Wrapper<T> w) {
            List<T> list = selectList(w);
            return list.isEmpty() ? null : list.get(0);
        }

        @SuppressWarnings("rawtypes")
        int updateByWrapper(Wrapper<T> w) {
            AbstractWrapper aw = (AbstractWrapper) w;
            Map<String, Object> sets = parseSqlSet(((com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?>) w).getSqlSet(),
                    aw.getParamNameValuePairs());
            int n = 0;
            for (T t : new ArrayList<>(rows.values())) {
                if (matches(t, w)) {
                    for (Map.Entry<String, Object> e : sets.entrySet()) {
                        ReflectionTestUtils.setField(t, snakeToCamel(e.getKey()), e.getValue());
                    }
                    n++;
                }
            }
            return n;
        }

        @SuppressWarnings("rawtypes")
        private boolean matches(T t, Wrapper<T> w) {
            AbstractWrapper aw = (AbstractWrapper) w;
            Map<String, Object> params = aw.getParamNameValuePairs();
            // MP 3.4.2 的 normal 段拆成：列、操作符、#{占位}，中间夹 AND
            List<String> segs = new ArrayList<>();
            for (Object segObj : aw.getExpression().getNormal()) {
                segs.add(((ISqlSegment) segObj).getSqlSegment());
            }
            for (int i = 0; i + 2 < segs.size(); ) {
                String col = segs.get(i);
                String op = segs.get(i + 1);
                Matcher vm = VALUE.matcher(segs.get(i + 2));
                if (COLUMN.matcher(col).matches() && OPS.contains(op) && vm.matches()) {
                    Object expect = params.get(vm.group(1));
                    Object actual = ReflectionTestUtils.getField(t, snakeToCamel(col));
                    int cmp = compareNullable(actual, expect);
                    if ("=".equals(op) && cmp != 0) {
                        return false;
                    }
                    if (">=".equals(op) && (actual == null || cmp < 0)) {
                        return false;
                    }
                    i += 3;
                    if (i < segs.size() && "AND".equalsIgnoreCase(segs.get(i))) {
                        i++;
                    }
                } else {
                    i++;
                }
            }
            return true;
        }

        private static final java.util.regex.Pattern COLUMN =
                java.util.regex.Pattern.compile("[a-z_]+");
        private static final java.util.List<String> OPS =
                java.util.Arrays.asList("=", ">=", ">", "<=", "<");
        private static final java.util.regex.Pattern VALUE = java.util.regex.Pattern.compile(
                "#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}");

        @SuppressWarnings("rawtypes")
        private List<String> parseOrder(Wrapper<T> w) {
            List<String> keys = new ArrayList<>();
            for (Object segObj : ((AbstractWrapper) w).getExpression().getOrderBy()) {
                String sql = ((ISqlSegment) segObj).getSqlSegment().trim();
                String[] parts = sql.split("\\s+");
                keys.add(parts[0]);
                keys.add(parts.length > 1 ? parts[1].toUpperCase() : "ASC");
            }
            return keys;
        }

        private Map<String, Object> parseSqlSet(String sqlSet, Map<String, Object> params) {
            Map<String, Object> out = new LinkedHashMap<>();
            Matcher m = Pattern.compile(
                    "([a-z_]+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}",
                    Pattern.CASE_INSENSITIVE).matcher(sqlSet);
            while (m.find()) {
                out.put(m.group(1), params.get(m.group(2)));
            }
            return out;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static int compareNullable(Object a, Object b) {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return -1;
            }
            if (b == null) {
                return 1;
            }
            if (a instanceof Number && b instanceof Number) {
                return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
            }
            return ((Comparable) a).compareTo(b);
        }

        private static String snakeToCamel(String s) {
            StringBuilder sb = new StringBuilder();
            boolean up = false;
            for (char c : s.toCharArray()) {
                if (c == '_') {
                    up = true;
                } else {
                    sb.append(up ? Character.toUpperCase(c) : c);
                    up = false;
                }
            }
            return sb.toString();
        }
    }
}
