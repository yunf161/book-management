package com.yunfeil161.ailibrary.book;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yunfeil161.ailibrary.book.catalog.dto.BookQueryRequest;
import com.yunfeil161.ailibrary.book.catalog.entity.BookDetailVO;
import com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper;
import com.yunfeil161.ailibrary.book.catalog.service.impl.BookServiceImpl;
import com.yunfeil161.ailibrary.book.category.dto.BookCategoryQueryRequest;
import com.yunfeil161.ailibrary.book.category.entity.BookCategory;
import com.yunfeil161.ailibrary.book.category.mapper.BookCategoryMapper;
import com.yunfeil161.ailibrary.book.category.service.impl.BookCategoryServiceImpl;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.book.copy.dto.BookCopyQueryRequest;
import com.yunfeil161.ailibrary.book.copy.entity.BookCopy;
import com.yunfeil161.ailibrary.book.copy.mapper.BookCopyMapper;
import com.yunfeil161.ailibrary.book.copy.service.impl.BookCopyServiceImpl;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 验证图书三个功能的纯 ID 查询确实复用 CacheClient 缓存。 */
class BookIdQueryCacheTests {

  private final Map<String, String> redisData = new ConcurrentHashMap<>();
  private CacheClient cacheClient;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    ValueOperations<String, String> valueOperations = mock(
      ValueOperations.class
    );
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(anyString())).thenAnswer(invocation ->
      redisData.get(invocation.getArgument(0))
    );
    doAnswer(invocation -> {
      redisData.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    })
      .when(valueOperations)
      .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    cacheClient = new CacheClient(redisTemplate);
  }

  @Test
  void categorySecondIdQueryUsesCache() {
    BookCategoryMapper mapper = mock(BookCategoryMapper.class);
    BookCategory category = new BookCategory();
    category.setId(11L);
    category.setName("缓存分类");
    when(mapper.findById(11L)).thenReturn(category);
    BookCategoryServiceImpl service = new BookCategoryServiceImpl(
      mapper,
      cacheClient,
      mock(PermissionAccess.class)
    );
    BookCategoryQueryRequest query = new BookCategoryQueryRequest();
    query.setId(11L);

    assertEquals(
      "缓存分类",
      service.getBookCategories(query).getRows().get(0).getName()
    );
    assertEquals(
      "缓存分类",
      service.getBookCategories(query).getRows().get(0).getName()
    );

    verify(mapper, times(1)).findById(11L);
    assertTrue(redisData.containsKey(BookCacheKeys.CATEGORY_BY_ID + 11L));
  }

  @Test
  void bookSecondIdQueryUsesCache() {
    BookMapper mapper = mock(BookMapper.class);
    BookCategoryMapper categoryMapper = mock(BookCategoryMapper.class);
    BookDetailVO book = new BookDetailVO();
    book.setId(21L);
    book.setTitle("缓存图书");
    when(mapper.findDetailById(21L)).thenReturn(book);
    BookServiceImpl service = new BookServiceImpl(
      mock(com.yunfeil161.ailibrary.recommendation.service.TagService.class),
      mapper,
      categoryMapper,
      cacheClient,
      mock(PermissionAccess.class)
    );
    BookQueryRequest query = new BookQueryRequest();
    query.setId(21L);

    assertEquals(
      "缓存图书",
      service.getBooks(query).getRows().get(0).getTitle()
    );
    assertEquals(
      "缓存图书",
      service.getBooks(query).getRows().get(0).getTitle()
    );

    verify(mapper, times(1)).findDetailById(21L);
    assertTrue(redisData.containsKey(BookCacheKeys.BOOK_BY_ID + 21L));
  }

  @Test
  void copySecondIdQueryUsesCache() {
    BookCopyMapper mapper = mock(BookCopyMapper.class);
    BookMapper bookMapper = mock(BookMapper.class);
    BookCopy copy = new BookCopy();
    copy.setId(31L);
    copy.setBarcode("CACHE-31");
    when(mapper.findById(31L)).thenReturn(copy);
    PermissionAccess access = mock(PermissionAccess.class);
    BookCopyServiceImpl service = new BookCopyServiceImpl(
      mapper,
      bookMapper,
      cacheClient,
      access
    );
    BookCopyQueryRequest query = new BookCopyQueryRequest();
    query.setId(31L);

    assertEquals(
      "CACHE-31",
      service.getBookCopies(query).getRows().get(0).getBarcode()
    );
    assertEquals(
      "CACHE-31",
      service.getBookCopies(query).getRows().get(0).getBarcode()
    );

    verify(mapper, times(1)).findById(31L);
    assertTrue(redisData.containsKey(BookCacheKeys.COPY_BY_ID + 31L));
  }

  @Test
  void missingIdsUseEmptyCacheAndDoNotQueryDatabaseTwice() {
    BookCategoryMapper categoryMapper = mock(BookCategoryMapper.class);
    BookMapper bookMapper = mock(BookMapper.class);
    BookCopyMapper copyMapper = mock(BookCopyMapper.class);
    PermissionAccess access = mock(PermissionAccess.class);
    BookCategoryServiceImpl categoryService = new BookCategoryServiceImpl(
      categoryMapper,
      cacheClient,
      access
    );
    BookServiceImpl bookService = new BookServiceImpl(
      mock(com.yunfeil161.ailibrary.recommendation.service.TagService.class),
      bookMapper,
      categoryMapper,
      cacheClient,
      access
    );
    BookCopyServiceImpl copyService = new BookCopyServiceImpl(
      copyMapper,
      bookMapper,
      cacheClient,
      access
    );

    BookCategoryQueryRequest categoryQuery = new BookCategoryQueryRequest();
    categoryQuery.setId(101L);
    BookQueryRequest bookQuery = new BookQueryRequest();
    bookQuery.setId(102L);
    BookCopyQueryRequest copyQuery = new BookCopyQueryRequest();
    copyQuery.setId(103L);

    assertThrows(NotFoundException.class, () ->
      categoryService.getBookCategories(categoryQuery)
    );
    assertThrows(NotFoundException.class, () ->
      categoryService.getBookCategories(categoryQuery)
    );
    assertThrows(NotFoundException.class, () ->
      bookService.getBooks(bookQuery)
    );
    assertThrows(NotFoundException.class, () ->
      bookService.getBooks(bookQuery)
    );
    assertThrows(NotFoundException.class, () ->
      copyService.getBookCopies(copyQuery)
    );
    assertThrows(NotFoundException.class, () ->
      copyService.getBookCopies(copyQuery)
    );

    verify(categoryMapper, times(1)).findById(101L);
    verify(bookMapper, times(1)).findDetailById(102L);
    verify(copyMapper, times(1)).findById(103L);
    assertEquals("", redisData.get(BookCacheKeys.CATEGORY_BY_ID + 101L));
    assertEquals("", redisData.get(BookCacheKeys.BOOK_BY_ID + 102L));
    assertEquals("", redisData.get(BookCacheKeys.COPY_BY_ID + 103L));
  }
}
