package com.yunfeil161.ailibrary.book.category.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.book.category.dto.BookCategoryCreateRequest;
import com.yunfeil161.ailibrary.book.category.dto.BookCategoryQueryRequest;
import com.yunfeil161.ailibrary.book.category.dto.BookCategoryUpdateRequest;
import com.yunfeil161.ailibrary.book.category.entity.BookCategory;
import com.yunfeil161.ailibrary.book.category.mapper.BookCategoryMapper;
import com.yunfeil161.ailibrary.book.category.service.BookCategoryService;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.common.redis.lock.BusinessLockKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.lock.LockMode;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookCategoryServiceImpl implements BookCategoryService {

  private final BookCategoryMapper mapper;
  private final CacheClient cacheClient;
  private final PermissionAccess permissionAccess;

  @Override
  @DistributedLock(
    keys = "'lock:resource:book-category:' + (#query != null && #query.id != null ? #query.id : 'query')",
    mode = LockMode.READ
  )
  public PageResult<BookCategory> getBookCategories(
    BookCategoryQueryRequest query
  ) {
    if (query == null) query = new BookCategoryQueryRequest();
    if (
      Boolean.TRUE.equals(query.getRootOnly()) && query.getParentId() != null
    ) {
      throw new WrongFormatException("rootOnly 与 parentId 不能同时使用");
    }
    if (isIdOnlyQuery(query)) {
      BookCategory category = cacheClient.queryWithPassThrough(
        BookCacheKeys.CATEGORY_BY_ID,
        query.getId(),
        BookCategory.class,
        mapper::findById,
        BookCacheKeys.ID_TTL_MINUTES,
        TimeUnit.MINUTES
      );
      if (category == null) throw new NotFoundException("未找到图书分类");
      return new PageResult<>(1L, List.of(category));
    }
    startPage(query.getPage(), query.getPageSize());
    List<BookCategory> rows = mapper.find(query);
    if (query.getId() != null && rows.isEmpty()) throw new NotFoundException(
      "未找到图书分类"
    );
    PageInfo<BookCategory> info = new PageInfo<>(rows);
    return new PageResult<>(info.getTotal(), info.getList());
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:book-category:create'")
  @Audited(resource = AuditResource.BOOK_CATEGORY, id = "")
  public BookCategory createBookCategory(BookCategoryCreateRequest request) {
    // 管理写操作必须由当前角色显式拥有对应权限，不能按成员类型数值推断。
    permissionAccess.require(PermissionCodes.BOOK_CATEGORY_CREATE);
    if (
      request == null || blank(request.getCode()) || blank(request.getName())
    ) {
      throw new WrongFormatException("分类编码和名称不能为空");
    }
    String code = request.getCode().trim();
    String name = request.getName().trim();
    if (mapper.countByCode(code) > 0) throw new ConflictException(
      "分类编码已存在"
    );
    requireActiveParent(request.getParentId());
    if (mapper.countSiblingName(request.getParentId(), name, null) > 0) {
      throw new ConflictException("同级分类名称已存在");
    }
    BookCategory category = new BookCategory();
    category.setCode(code);
    category.setName(name);
    category.setParentId(request.getParentId());
    category.setDescription(request.getDescription());
    category.setStatus(CatalogStatus.ACTIVE);
    category.setVersion(0L);
    if (mapper.insert(category) != 1 || category.getId() == null) {
      throw new IllegalStateException("新增图书分类失败");
    }
    cacheClient.deleteRequired(BookCacheKeys.CATEGORY_BY_ID + category.getId());
    return mapper.findById(category.getId());
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:book-category:' + #id")
  @Audited(resource = AuditResource.BOOK_CATEGORY, id = "#id")
  public BookCategory updateBookCategory(
    Long id,
    BookCategoryUpdateRequest request
  ) {
    // 在读取或修改数据前完成授权，避免缓存或数据库结果绕过权限判断。
    permissionAccess.require(PermissionCodes.BOOK_CATEGORY_UPDATE);
    if (
      id == null ||
      request == null ||
      request.getExpectedVersion() == null ||
      request.getExpectedVersion() < 0
    ) {
      throw new WrongFormatException("分类ID和有效的expectedVersion不能为空");
    }
    BookCategory existing = requireCategory(id);
    if (request.getName() != null && blank(request.getName())) {
      throw new WrongFormatException("分类名称不能为空白字符串");
    }
    Long targetParent = request.isParentIdProvided()
      ? request.getParentId()
      : existing.getParentId();
    if (id.equals(targetParent)) throw new ConflictException(
      "分类不能以自身为父分类"
    );
    requireActiveParent(targetParent);
    ensureNoCycle(id, targetParent);
    String targetName =
      request.getName() == null ? existing.getName() : request.getName().trim();
    if (mapper.countSiblingName(targetParent, targetName, id) > 0) {
      throw new ConflictException("同级分类名称已存在");
    }
    int rows = mapper.update(id, request);
    if (rows == 0) {
      if (mapper.findById(id) == null) throw new NotFoundException(
        "图书分类已被删除"
      );
      throw new ConflictException("图书分类已被其他用户修改，请刷新后重试");
    }
    if (rows != 1) throw new IllegalStateException(
      "图书分类更新影响了多条记录"
    );
    cacheClient.deleteRequired(BookCacheKeys.CATEGORY_BY_ID + id);
    return mapper.findById(id);
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:book-category:' + #id")
  @Audited(resource = AuditResource.BOOK_CATEGORY, id = "#id")
  public String deleteBookCategory(Long id) {
    // 删除权限与新增、修改权限相互独立。
    permissionAccess.require(PermissionCodes.BOOK_CATEGORY_DELETE);
    if (id == null) throw new WrongFormatException("分类ID不能为空");
    BookCategory existing = requireCategory(id);
    if (
      existing.getStatus() == CatalogStatus.DISABLED
    ) return existing.getName();
    if (mapper.countChildren(id) > 0) throw new ConflictException(
      "分类仍有子分类，不能删除"
    );
    if (mapper.disable(id, existing.getVersion()) != 1) {
      throw new ConflictException("图书分类状态或版本已变化，请刷新后重试");
    }
    cacheClient.deleteRequired(BookCacheKeys.CATEGORY_BY_ID + id);
    return existing.getName();
  }

  private void requireActiveParent(Long parentId) {
    if (parentId == null) return;
    BookCategory parent = mapper.findById(parentId);
    if (parent == null) throw new NotFoundException("父分类不存在");
    if (parent.getStatus() != CatalogStatus.ACTIVE) throw new ConflictException(
      "父分类已停用"
    );
  }

  private void ensureNoCycle(Long id, Long parentId) {
    Set<Long> visited = new HashSet<>();
    Long current = parentId;
    while (current != null) {
      if (id.equals(current)) throw new ConflictException(
        "不能将当前分类的后代设置为父分类"
      );
      if (!visited.add(current)) throw new ConflictException(
        "分类层级中已存在循环引用"
      );
      BookCategory category = mapper.findById(current);
      if (category == null) throw new NotFoundException("父分类不存在");
      current = category.getParentId();
    }
  }

  private BookCategory requireCategory(Long id) {
    BookCategory category = mapper.findById(id);
    if (category == null) throw new NotFoundException("未找到图书分类");
    return category;
  }

  private void startPage(Integer page, Integer size) {
    PageHelper.startPage(
      page == null || page < 1 ? 1 : page,
      size == null || size < 1 ? 10 : Math.min(size, 100)
    );
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private boolean isIdOnlyQuery(BookCategoryQueryRequest query) {
    return (
      query.getId() != null &&
      query.getCode() == null &&
      query.getName() == null &&
      query.getParentId() == null &&
      query.getDescription() == null &&
      query.getStatus() == null &&
      query.getVersion() == null &&
      query.getCreatedAt() == null &&
      query.getUpdatedAt() == null &&
      query.getRootOnly() == null
    );
  }
}
