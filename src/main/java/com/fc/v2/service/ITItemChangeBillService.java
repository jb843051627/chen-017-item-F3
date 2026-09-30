package com.fc.v2.service;

import com.fc.v2.model.auto.TItemChangeBill;

/**
 * 事项要素调整逐级核签单 Service接口（approval-chain 形状：县核/市核/省定三道口，无增删改查入口）
 *
 * <p>全单只有一对落点——签与退，三道口都经 {@link #approve} / {@link #reject} 办；
 * 页面不另开能签、能退、能改名的门。落了几个名不问屏上的框，只由签迹顺着点回。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITItemChangeBillService {

    /** 按主键回查单据 */
    TItemChangeBill selectTItemChangeBillById(Long id);

    /**
     * 县核起件：四样（事项码、改动前几栏、改动后几栏、改的缘故）缺一不可，
     * 且承办底册须先过前置检。四样缺一、底册不过，件起不来（返回 null）。
     * 同一事项当年第二件，当场点名（记于备注，不拦报送）。
     */
    TItemChangeBill createBill(TItemChangeBill draft);

    /**
     * 签一笔（旧签法，入参与返回不改）：按当前所在道次落一名；
     * 省定为两名点齐之口，一名只留在本口，齐了才往结案走。
     * 被拒（件不在核、跳口、同人当口再落）返回 null。
     */
    TItemChangeBill approve(Long id, String approver, String comment);

    /**
     * 并排新签法：与旧法同名并排而立，专办省定两口各盯一笔
     * （role：1 盯要素齐不齐，2 盯与上位名录合不合）。两笔各记各的，
     * 掐同一分钟也按两笔算；只到一名，件仍卡省定；两种分工各齐才算落章。
     */
    TItemChangeBill approve(Long id, String approver, int role, String comment);

    /**
     * 退（旧签法，入参与返回不改）：哪一口写下不予通过，件就此停在该口，
     * 往后既不追加也不改写（返回落定后的单据）。
     */
    TItemChangeBill reject(Long id, String approver, String comment);

    /**
     * 并排新退法：省里以表述不合规矩打回——先前各口落过的名目整批沉底，
     * 件从县核那一口重新一层层往上攒；沉下去的那批与新补的那批不并排。
     */
    TItemChangeBill reject(Long id, String approver, int role, String comment);

    /** 承办人把件撤回报送：件停在当前口，不再追加改写。被拒返回 null。 */
    TItemChangeBill withdraw(Long id, String operator, String comment);

    /** 旧法保留：退回上一环节（入参与返回不改）。 */
    TItemChangeBill rollback(Long id, String comment);

    /**
     * 顺着签迹点当前口已落名数——屏上摆的数、心里记的数都不作数，
     * 签没签成只看它给回什么。
     */
    int countSigns(Long id);

    /**
     * 三道口感完后的倒数复核：改定版要素当日在名录上的记载从省定倒数回县核，
     * 各级落过的名目一笔不差，每口经得起复核；对外名录与档内卷底由同一回点数
     * 交出，两头名次合不拢即未齐。合得上返回 true。
     */
    boolean verify(Long id);
}
