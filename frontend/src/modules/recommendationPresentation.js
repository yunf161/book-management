/** 显示模式以推荐接口返回的真实偏好为准，不从浏览器旧资料推断。 */
export function recommendationPresentation(personalizationEnabled) {
  return personalizationEnabled === true
    ? {
        title: "为你推荐",
        description: "根据兴趣标签、馆藏热度与新书推荐",
        empty: "暂无当前推荐，请选择兴趣或点击换一批推荐",
      }
    : {
        title: "热门图书推荐",
        description: "根据馆藏热度、新书、库存与分类多样性推荐，不使用个人兴趣",
        empty: "暂无热门图书推荐，请点击换一批推荐",
      };
}
