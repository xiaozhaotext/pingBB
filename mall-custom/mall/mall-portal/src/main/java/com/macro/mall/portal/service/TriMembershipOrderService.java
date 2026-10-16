package com.macro.mall.portal.service;

import com.macro.mall.common.membership.TriPoolService;
import com.macro.mall.common.exception.ApiException;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.ZoneRules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.macro.mall.common.membership.TriPoolRules.*;

/** Only an explicitly designated, standalone PICK membership-card order activates membership. */
@Service
public class TriMembershipOrderService {
    private final TriPoolService pools;
    private final JdbcTemplate jdbc;
    private final PointsWalletService wallets;
    public TriMembershipOrderService(TriPoolService pools,JdbcTemplate jdbc,PointsWalletService wallets) {
        this.pools=pools;this.jdbc=jdbc;this.wallets=wallets;
    }
    public boolean enabled() { return pools.enabled(); }
    public boolean isMember(long id) { return pools.isMember(id); }

    public void snapshot(OmsOrder order,List<OmsOrderItem> items) { /* Membership is purchased separately in My Account. */ }
    public boolean fulfill(long orderId) { return false; }
}
