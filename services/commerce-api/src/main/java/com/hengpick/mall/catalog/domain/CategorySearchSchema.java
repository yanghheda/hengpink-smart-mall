package com.hengpick.mall.catalog.domain;

import java.util.List;
import java.util.Map;

/** 类目可用于硬条件搜索的属性与操作符快照。 */
public record CategorySearchSchema(String categoryId, Map<String, List<String>> hardConstraintOperators) {}
