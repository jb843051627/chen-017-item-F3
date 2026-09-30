package com.fc.v2.service.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import com.fc.v2.service.ITItemChangeBillService;
import com.fc.v2.util.StringUtils;

/**
 * 事项要素调整逐级核签单 Service业务层处理
 * （approval-chain 形状：县核/市核/省定三道口，签与退一对落点）
 *
 * <p>件的推进按口次算：0县核 → 1市核 → 2省定，不按谁催得急算；跳口、倒口都不接。
 * 落名数只从 t_item_change_sign 签迹顺着点回，单据上的 sign_count 由签迹反写，
 * 屏上摆的框、承办人心里记的数都不作数。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TItemChangeBillServiceImpl implements ITItemChangeBillService {

    /** 道次：县核收材料 */
    private static final int NODE_COUNTY = 0;
    /** 道次：市核做对照 */
    private static final int NODE_CITY = 1;
    /** 道次：省定落章并同步对外名录 */
    private static final int NODE_PROVINCE = 2;
    private static final int MAX_NODE = NODE_PROVINCE;

    /** 同口并签办法：任一人 */
    private static final int MODE_OR = 0;
    /** 同口并签办法：两名点齐（省定专用） */
    private static final int MODE_AND = 1;

    /** 报批情形：在核（省定打回后回县核重走的新一轮，仍落此情形；打回痕迹在轮次与备注里） */
    private static final int STATUS_RUNNING = 0;
    /** 报批情形：已核讫（旧签法之结案态，复核同样认） */
    private static final int STATUS_PASS = 1;
    /** 报批情形：不予通过，止步该口，不追加不改写 */
    private static final int STATUS_REJECTED = 3;
    /** 报批情形：承办人撤回报送，止步当前口 */
    private static final int STATUS_WITHDRAWN = 4;
    /** 报批情形：已落章封住（同已核讫，再动须另开一张单） */
    private static final int STATUS_SEALED = 5;

    /** 省定并排分工：不分工（旧签法落笔） */
    private static final int ROLE_NONE = 0;
    /** 省定并排分工：盯要素齐不齐 */
    private static final int ROLE_ELEMENTS = 1;
    /** 省定并排分工：盯与上位名录合不合规矩 */
    private static final int ROLE_REGISTRY = 2;

    /** 对外名录版次：已上柜 */
    private static final int VER_STAGE_PUBLISHED = 3;
    /** 版次落点：在办 */
    private static final int VER_STATUS_ACTIVE = 1;

    @javax.annotation.Resource
    private TItemChangeBillMapper itemChangeBillMapper;

    @javax.annotation.Resource
    private TItemChangeSignMapper itemChangeSignMapper;

    @javax.annotation.Resource
    private TItemDeptArchMapper itemDeptArchMapper;

    @javax.annotation.Resource
    private TItemCatalogRowMapper itemCatalogRowMapper;

    @javax.annotation.Resource
    private TItemCatalogVerMapper itemCatalogVerMapper;

    @Override
    public TItemChangeBill selectTItemChangeBillById(Long id) {
        return this.itemChangeBillMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill createBill(TItemChangeBill draft) {
        if (draft == null) {
            return null;
        }
        // 县核先点四样：事项码、改动前那几栏、改动后那几栏、改的缘故——缺一样，件起不来。
        if (StringUtils.isEmpty(draft.getItemCode())
                || StringUtils.isEmpty(draft.getBeforeText())
                || StringUtils.isEmpty(draft.getAfterText())
                || StringUtils.isEmpty(draft.getChangeReason())) {
            return null;
        }
        // 前置检：承办底册须在编；底册不过前置那道检，件停在县核，往后再齐也不往前挪。
        if (StringUtils.isEmpty(draft.getSiteNo())) {
            return null;
        }
        TItemDeptArch arch = this.itemDeptArchMapper.selectOne(new QueryWrapper<TItemDeptArch>()
                .eq("site_no", draft.getSiteNo())
                .eq("del_flag", 0));
        if (arch == null || arch.getStatus() == null || arch.getStatus() != 0) {
            return null;
        }

        draft.setId(null);
        draft.setNodeNo(NODE_COUNTY);
        draft.setRoundNo(1);
        // 县核一口一人签即过；needCount/signCount 不作填报框，由签迹点回后反写。
        draft.setSignMode(MODE_OR);
        draft.setNeedCount(1);
        draft.setSignCount(0);
        draft.setStatus(STATUS_RUNNING);
        draft.setDelFlag(0);
        // 同一事项当年第二件要报的，当场点名（记于备注，不拦报送）。
        int yearly = countYearlyBills(draft.getItemCode());
        if (yearly >= 1) {
            draft.setRemark(StringUtils.isNotEmpty(draft.getRemark())
                    ? draft.getRemark() + "；当年第" + (yearly + 1) + "件，当场点名"
                    : "当年第" + (yearly + 1) + "件，当场点名");
        }
        this.itemChangeBillMapper.insert(draft);
        return draft;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill approve(Long id, String approver, String comment) {
        return sign(id, approver, ROLE_NONE, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill approve(Long id, String approver, int role, String comment) {
        if (role != ROLE_ELEMENTS && role != ROLE_REGISTRY) {
            return null;
        }
        return sign(id, approver, role, comment);
    }

    /**
     * 落一笔签：件须在核、人须到场、当前口须接这笔；
     * 同人当轮当口只算一笔（替人代落的那一笔不论写了什么都不作数）。
     */
    private TItemChangeBill sign(Long id, String approver, int role, String comment) {
        TItemChangeBill r = loadActive(id);
        if (r == null || StringUtils.isEmpty(approver)) {
            return null;
        }
        int node = r.getNodeNo() == null ? NODE_COUNTY : r.getNodeNo();
        int round = r.getRoundNo() == null ? 1 : r.getRoundNo();

        // 省定一口只接两人并排、分工各一的签法；无分工的旧签笔到了这一口不接，
        // 免得落下不分工的名目把件拖进卡而不结的境地。
        if (node == NODE_PROVINCE && role == ROLE_NONE) {
            return null;
        }
        // 反过来，带分工的并排签法专办省定，县核、市核不接。
        if (node != NODE_PROVINCE && role != ROLE_NONE) {
            return null;
        }
        // 市核对照省册：认的是事项码。码在上级下发册里查不到，这一口不接。
        if (node == NODE_CITY && !itemCodeListed(r.getItemCode())) {
            return null;
        }

        List<TItemChangeSign> nodeSigns = listRoundNodeSigns(id, round, node);
        // 同人当轮当口已落过：不留给谁代笔的口子，重复那一笔不作数。
        for (TItemChangeSign s : nodeSigns) {
            if (approver.equals(s.getSigner())) {
                return null;
            }
        }
        // 省定一口同一种分工不得由同一人再落（两人并排、两笔各记各的）。
        if (node == NODE_PROVINCE && role != ROLE_NONE) {
            for (TItemChangeSign s : nodeSigns) {
                if (role == (s.getSignRole() == null ? ROLE_NONE : s.getSignRole())) {
                    return null;
                }
            }
        }

        TItemChangeSign sign = new TItemChangeSign();
        sign.setBillId(id);
        sign.setRoundNo(round);
        sign.setNodeNo(node);
        sign.setSigner(approver);
        sign.setSignRole(node == NODE_PROVINCE ? role : ROLE_NONE);
        sign.setSignTime(new Date());
        sign.setDelFlag(0);
        sign.setRemark(comment);
        this.itemChangeSignMapper.insert(sign);

        // 落了几个名顺着签迹点回来反写，不设填的框。
        nodeSigns = listRoundNodeSigns(id, round, node);
        r.setSignCount(nodeSigns.size());

        if (nodeFulfilled(node, nodeSigns)) {
            if (node == NODE_PROVINCE) {
                // 省定落章：要素改动落定，全单各栏一起锁住；对外名录跟着走这一道。
                sealBill(r, nodeSigns);
            } else {
                // 下一道接上：上一道才算没白报。
                r.setNodeNo(node + 1);
                r.setSignMode(node + 1 == NODE_PROVINCE ? MODE_AND : MODE_OR);
                r.setNeedCount(node + 1 == NODE_PROVINCE ? 2 : 1);
                r.setSignCount(0);
                r.setStatus(STATUS_RUNNING);
                this.itemChangeBillMapper.updateById(r);
            }
        } else {
            // 只到一名，件仍卡在这一口，见不着结案。
            r.setStatus(STATUS_RUNNING);
            this.itemChangeBillMapper.updateById(r);
        }
        return r;
    }

    /** 一口签没签成只看签迹：县核/市核一人即可；省定须两种分工各一笔、且不是同一人。 */
    private boolean nodeFulfilled(int node, List<TItemChangeSign> signs) {
        if (node != NODE_PROVINCE) {
            return !signs.isEmpty();
        }
        String elementsBy = null;
        String registryBy = null;
        for (TItemChangeSign s : signs) {
            int role = s.getSignRole() == null ? ROLE_NONE : s.getSignRole();
            if (role == ROLE_ELEMENTS) {
                elementsBy = s.getSigner();
            } else if (role == ROLE_REGISTRY) {
                registryBy = s.getSigner();
            }
        }
        return elementsBy != null && registryBy != null && !elementsBy.equals(registryBy);
    }

    /** 省定落章后的封单与对外名录同步：同一回点数，档里与对外两头名次合不拢便算没齐。 */
    private void sealBill(TItemChangeBill r, List<TItemChangeSign> provinceSigns) {
        int total = countRoundSigns(r.getId(), r.getRoundNo());
        // 同一回点数先交对外名录，再封档；两头名次须由这一回点数同时交出。
        syncPublicRegistry(r, total);
        r.setNodeNo(MAX_NODE);
        r.setSignCount(provinceSigns.size());
        r.setStatus(STATUS_SEALED);
        r.setRemark(appendNote(r.getRemark(), "省定已落章，全单封住，落名共" + total + "笔"));
        this.itemChangeBillMapper.updateById(r);
    }

    /** 对外名录同步一份：改定版要素上柜，记载里带住本单档内卷底的同一回点数。 */
    private void syncPublicRegistry(TItemChangeBill r, int total) {
        TItemCatalogVer ver = new TItemCatalogVer();
        ver.setBizNo(r.getBillNo());
        ver.setStage(VER_STAGE_PUBLISHED);
        ver.setStatus(VER_STATUS_ACTIVE);
        ver.setContent(encodePublished(r.getItemCode(), r.getAfterText()));
        ver.setLastAction("changeBill:" + r.getId() + ":" + total);
        ver.setDelFlag(0);
        this.itemCatalogVerMapper.insert(ver);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill reject(Long id, String approver, String comment) {
        TItemChangeBill r = loadActive(id);
        if (r == null || StringUtils.isEmpty(approver)) {
            return null;
        }
        // 哪一口写下不予通过，这一件就此停在该口，往后既不追加也不改写。
        int node = r.getNodeNo() == null ? NODE_COUNTY : r.getNodeNo();
        r.setStatus(STATUS_REJECTED);
        r.setRemark(appendNote(r.getRemark(),
                "第" + (node + 1) + "道次不予通过（" + approver + "）：" + safe(comment)));
        this.itemChangeBillMapper.updateById(r);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill reject(Long id, String approver, int role, String comment) {
        if (role != ROLE_ELEMENTS && role != ROLE_REGISTRY) {
            return null;
        }
        TItemChangeBill r = loadActive(id);
        if (r == null || StringUtils.isEmpty(approver)) {
            return null;
        }
        int node = r.getNodeNo() == null ? NODE_COUNTY : r.getNodeNo();
        // “表述不合规矩打回、整批沉底、回县核重走”只属省定一口；
        // 县核、市核写不通过走旧退法，止步该口。
        if (node != NODE_PROVINCE) {
            return null;
        }
        int round = r.getRoundNo() == null ? 1 : r.getRoundNo();
        // 省里以表述不合规矩打回：先前各口落过的名目整批沉底（置删不物理删），
        // 沉下去的那批与新补的那批不并排摆。
        this.itemChangeSignMapper.update(null, new UpdateWrapper<TItemChangeSign>()
                .eq("bill_id", id)
                .eq("round_no", round)
                .eq("del_flag", 0)
                .set("del_flag", 1));
        // 件从县核那一口重新一层层往上攒。
        r.setRoundNo(round + 1);
        r.setNodeNo(NODE_COUNTY);
        r.setSignMode(MODE_OR);
        r.setNeedCount(1);
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
        r.setRemark(appendNote(r.getRemark(),
                "省定以表述不合规矩打回（" + approver + "，分工" + role + "），第" + round
                        + "轮签迹整批沉底，回县核重走：" + safe(comment)));
        this.itemChangeBillMapper.updateById(r);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill withdraw(Long id, String operator, String comment) {
        TItemChangeBill r = loadActive(id);
        if (r == null || StringUtils.isEmpty(operator)) {
            return null;
        }
        // 承办人把件撤回报送：停在当前口，不再追加、不改写。
        r.setStatus(STATUS_WITHDRAWN);
        int node = r.getNodeNo() == null ? NODE_COUNTY : r.getNodeNo();
        r.setRemark(appendNote(r.getRemark(),
                operator + "于第" + (node + 1) + "道次撤回报送：" + safe(comment)));
        this.itemChangeBillMapper.updateById(r);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TItemChangeBill rollback(Long id, String comment) {
        TItemChangeBill r = this.itemChangeBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? NODE_COUNTY : r.getNodeNo();
        r.setNodeNo(Math.max(NODE_COUNTY, node - 1));
        this.itemChangeBillMapper.updateById(r);
        return r;
    }

    @Override
    public int countSigns(Long id) {
        TItemChangeBill r = this.itemChangeBillMapper.selectById(id);
        if (r == null) {
            return 0;
        }
        int node = r.getNodeNo() == null ? NODE_COUNTY : r.getNodeNo();
        int round = r.getRoundNo() == null ? 1 : r.getRoundNo();
        // 顺着签迹点当前口；屏上摆的数、承办人心里记的数都不作数。
        return listRoundNodeSigns(id, round, node).size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, readOnly = true)
    public boolean verify(Long id) {
        TItemChangeBill r = this.itemChangeBillMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 须是省定落章封住的件。
        if (r.getStatus() == null
                || (r.getStatus() != STATUS_PASS && r.getStatus() != STATUS_SEALED)
                || (r.getNodeNo() != null && r.getNodeNo() != NODE_PROVINCE)) {
            return false;
        }
        int round = r.getRoundNo() == null ? 1 : r.getRoundNo();

        // 从省定倒数回县核：各级落过的名目一笔不差，每一口都经得起复核。
        String[][] seen = new String[MAX_NODE + 1][];
        for (int node = NODE_PROVINCE; node >= NODE_COUNTY; node--) {
            List<TItemChangeSign> signs = listRoundNodeSigns(id, round, node);
            if (!nodeFulfilled(node, signs)) {
                return false;
            }
            seen[node] = signerArray(signs);
        }
        int archivedTotal = 0;
        for (String[] names : seen) {
            archivedTotal += names.length;
        }

        // 对外名录当日记载：同一事项、改定版要素、同一回点数，两头名次合不拢便算没齐。
        TItemCatalogVer published = this.itemCatalogVerMapper.selectOne(new QueryWrapper<TItemCatalogVer>()
                .eq("biz_no", r.getBillNo())
                .eq("stage", VER_STAGE_PUBLISHED)
                .eq("del_flag", 0)
                .orderByDesc("id")
                .last("limit 1"));
        if (published == null) {
            return false;
        }
        String expectContent = encodePublished(r.getItemCode(), r.getAfterText());
        if (!expectContent.equals(published.getContent())) {
            return false;
        }
        Integer publicTotal = parseTotal(published.getLastAction(), id);
        return publicTotal != null && publicTotal == archivedTotal;
    }

    // ------------------------------------------------------------------

    /** 取在核之件；已核讫/封住/不予通过/撤回的件，谁也不能再动。 */
    private TItemChangeBill loadActive(Long id) {
        if (id == null) {
            return null;
        }
        TItemChangeBill r = this.itemChangeBillMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag() != 0)) {
            return null;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        return r;
    }

    private List<TItemChangeSign> listRoundNodeSigns(Long billId, int round, int node) {
        return this.itemChangeSignMapper.selectList(new QueryWrapper<TItemChangeSign>()
                .eq("bill_id", billId)
                .eq("round_no", round)
                .eq("node_no", node)
                .eq("del_flag", 0)
                .orderByAsc("id"));
    }

    private int countRoundSigns(Long billId, int round) {
        Integer cnt = this.itemChangeSignMapper.selectCount(new QueryWrapper<TItemChangeSign>()
                .eq("bill_id", billId)
                .eq("round_no", round)
                .eq("del_flag", 0));
        return cnt == null ? 0 : cnt;
    }

    /** 市核对照省下发册：认事项码，不认名号；名号撞得一个不差也不顶用。 */
    private boolean itemCodeListed(String itemCode) {
        if (StringUtils.isEmpty(itemCode)) {
            return false;
        }
        Integer cnt = this.itemCatalogRowMapper.selectCount(new QueryWrapper<TItemCatalogRow>()
                .eq("item_code", itemCode)
                .eq("del_flag", 0));
        return cnt != null && cnt > 0;
    }

    /** 同一事项当年已报过几件（按创建时间落公历年）。 */
    private int countYearlyBills(String itemCode) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Integer cnt = this.itemChangeBillMapper.selectCount(new QueryWrapper<TItemChangeBill>()
                .eq("item_code", itemCode)
                .eq("del_flag", 0)
                .ge("create_time", cal.getTime()));
        return cnt == null ? 0 : cnt;
    }

    private String[] signerArray(List<TItemChangeSign> signs) {
        String[] arr = new String[signs.size()];
        for (int i = 0; i < signs.size(); i++) {
            arr[i] = signs.get(i).getSigner();
        }
        return arr;
    }

    private String encodePublished(String itemCode, String afterText) {
        return itemCode + "|" + afterText;
    }

    /** last_action 形如 changeBill:{billId}:{total}，只认本单那一回点数。 */
    private Integer parseTotal(String lastAction, Long billId) {
        if (StringUtils.isEmpty(lastAction)) {
            return null;
        }
        String prefix = "changeBill:" + billId + ":";
        if (!lastAction.startsWith(prefix)) {
            return null;
        }
        try {
            return Integer.valueOf(lastAction.substring(prefix.length()).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String appendNote(String oldNote, String note) {
        return StringUtils.isNotEmpty(oldNote) ? oldNote + "；" + note : note;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
