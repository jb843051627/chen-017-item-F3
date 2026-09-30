package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 事项要素调整逐级核签单对象 t_item_change_bill
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_item_change_bill")
@ApiModel(value = "TItemChangeBill", description = "事项要素调整逐级核签单")
public class TItemChangeBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 要素调整核签单号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "要素调整核签单号")
    private String billNo;

    /** 事项码（县核点验头一样，市核只认码不认名号） */
    @TableField("item_code")
    @ApiModelProperty(value = "事项码（县核点验头一样，市核只认码不认名号）")
    private String itemCode;

    /** 改动前那几栏 */
    @TableField("before_text")
    @ApiModelProperty(value = "改动前那几栏")
    private String beforeText;

    /** 改动后那几栏 */
    @TableField("after_text")
    @ApiModelProperty(value = "改动后那几栏")
    private String afterText;

    /** 改的缘故 */
    @TableField("change_reason")
    @ApiModelProperty(value = "改的缘故")
    private String changeReason;

    /** 承办底册代号（前置检落点，须在编底册） */
    @TableField("site_no")
    @ApiModelProperty(value = "承办底册代号（前置检落点，须在编底册）")
    private String siteNo;

    /** 本事项第几轮报送（打回重走一轮加一，沉底那批不与新批并排） */
    @TableField("round_no")
    @ApiModelProperty(value = "本事项第几轮报送（打回重走一轮加一，沉底那批不与新批并排）")
    private Integer roundNo;

    /** 当前所在道次 0..2（县核/市核/省定） */
    @TableField("node_no")
    @ApiModelProperty(value = "当前所在道次 0..2（县核/市核/省定）")
    private Integer nodeNo;

    /** 同口并签办法 0任一人 1两名点齐 */
    @TableField("sign_mode")
    @ApiModelProperty(value = "同口并签办法 0任一人 1两名点齐")
    private Integer signMode;

    /** 本口应落名数 */
    @TableField("need_count")
    @ApiModelProperty(value = "本口应落名数")
    private Integer needCount;

    /** 本口已落名数 */
    @TableField("sign_count")
    @ApiModelProperty(value = "本口已落名数")
    private Integer signCount;

    /** 报批情形 0在核 1已核讫 2已打回 3不予通过(止步) 4已撤回(止步) 5已落章封住 */
    @TableField("status")
    @ApiModelProperty(value = "报批情形 0在核 1已核讫 2已打回 3不予通过(止步) 4已撤回(止步) 5已落章封住")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getBeforeText() {
        return beforeText;
    }

    public void setBeforeText(String beforeText) {
        this.beforeText = beforeText;
    }

    public String getAfterText() {
        return afterText;
    }

    public void setAfterText(String afterText) {
        this.afterText = afterText;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getSignMode() {
        return signMode;
    }

    public void setSignMode(Integer signMode) {
        this.signMode = signMode;
    }

    public Integer getNeedCount() {
        return needCount;
    }

    public void setNeedCount(Integer needCount) {
        this.needCount = needCount;
    }

    public Integer getSignCount() {
        return signCount;
    }

    public void setSignCount(Integer signCount) {
        this.signCount = signCount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
