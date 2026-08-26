package com.hengpick.mall.catalog.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CatalogMapper {
    @Select("""
            SELECT p.id AS productId, p.category_id AS categoryId, c.name AS categoryName,
                   p.brand, p.model, p.display_name AS displayName, p.subtitle,
                   p.canonical_specs_json AS canonicalSpecsJson,
                   p.selling_points_json AS sellingPointsJson, p.limitation_json AS limitationJson,
                   p.warranty_summary AS warrantySummary, p.dataset_version AS datasetVersion,
                   p.is_simulated AS simulated, COUNT(s.id) AS skuCount
            FROM products p
            JOIN categories c ON c.id = p.category_id AND c.status = 'ACTIVE'
            LEFT JOIN skus s ON s.product_id = p.id AND s.status = 'ACTIVE'
            WHERE p.status = 'ACTIVE'
            GROUP BY p.id, p.category_id, c.name, p.brand, p.model, p.display_name, p.subtitle,
                     p.canonical_specs_json, p.selling_points_json, p.limitation_json,
                     p.warranty_summary, p.dataset_version, p.is_simulated
            ORDER BY p.created_at DESC, p.id
            LIMIT #{size} OFFSET #{offset}
            """)
    List<ProductRow> findPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM products WHERE status = 'ACTIVE'")
    long countActive();

    @Select("""
            SELECT p.id AS productId, p.category_id AS categoryId, c.name AS categoryName,
                   p.brand, p.model, p.display_name AS displayName, p.subtitle,
                   p.canonical_specs_json AS canonicalSpecsJson,
                   p.selling_points_json AS sellingPointsJson, p.limitation_json AS limitationJson,
                   p.warranty_summary AS warrantySummary, p.dataset_version AS datasetVersion,
                   p.is_simulated AS simulated, 0 AS skuCount
            FROM products p JOIN categories c ON c.id = p.category_id
            WHERE p.id = #{productId} AND p.status = 'ACTIVE' AND c.status = 'ACTIVE'
            """)
    ProductRow findProduct(@Param("productId") String productId);

    @Select("""
            SELECT id AS skuId, sku_code AS skuCode, display_name AS displayName,
                   attributes_json AS attributesJson, stock_status AS stockStatus,
                   stock_quantity AS stockQuantity, warranty_months AS warrantyMonths
            FROM skus
            WHERE product_id = #{productId} AND status = 'ACTIVE'
            ORDER BY created_at, id
            """)
    List<SkuRow> findSkus(@Param("productId") String productId);
}
