package com.hengpick.mall.pricing.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 报价只读查询 Mapper。 */
@Mapper
public interface OfferMapper {

    @Select("""
            SELECT id AS offerId, sku_id AS skuId, shop_id AS shopId,
                   list_price AS listPrice, sale_price AS salePrice, additional_fee AS additionalFee,
                   currency, valid_from AS validFrom, valid_to AS validTo,
                   dataset_version AS datasetVersion, version
            FROM offers
            WHERE sku_id = #{skuId}
              AND status = 'ACTIVE'
              AND valid_from <= #{calculationAt}
              AND valid_to > #{calculationAt}
            ORDER BY sale_price, id
            """)
    List<OfferRow> findValidOffers(
            @Param("skuId") String skuId,
            @Param("calculationAt") LocalDateTime calculationAt);
}
