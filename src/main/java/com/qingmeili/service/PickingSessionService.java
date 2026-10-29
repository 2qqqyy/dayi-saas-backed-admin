package com.qingmeili.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qingmeili.common.result.Result;
import com.qingmeili.dto.picking.PickingSessionDTO;
import com.qingmeili.entity.PickingSession;

import java.util.List;

/**
 * 采摘档期服务
 */
public interface PickingSessionService extends IService<PickingSession> {

    Result<List<PickingSession>> listByMerchant(Long merchantId, Integer status);

    Result<PickingSession> create(PickingSessionDTO dto);

    Result<PickingSession> update(Long id, PickingSessionDTO dto);

    Result<Void> updateStatus(Long id, Integer status);

    Result<Void> delete(Long id);
}
