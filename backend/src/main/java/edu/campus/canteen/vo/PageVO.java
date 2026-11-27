package edu.campus.canteen.vo;

import java.util.List;

/** 与 API 设计约定一致的分页响应。 */
public record PageVO<T>(List<T> records, long total, int page, int size) {
}
