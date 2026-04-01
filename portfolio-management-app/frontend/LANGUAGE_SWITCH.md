# 🌐 全局中英文切换功能

## ✨ 功能特性

- **一键切换**: 点击导航栏右侧的国旗图标即可在中英文之间切换
- **全局生效**: 所有页面内容实时翻译
- **持久保存**: 自动保存语言选择到本地存储
- **响应式设计**: 支持桌面端和移动端

## 🎯 使用方法

### 切换语言
点击页面顶部导航栏右侧的按钮：
- 🇨🇳 当前为中文，点击切换到英文
- 🇺🇸 当前为英文，点击切换到中文

### 效果预览
切换后，以下所有内容会自动翻译：
- ✅ 导航菜单（仪表盘/投资组合/市场行情/投资分析）
- ✅ 页面标题和副标题
- ✅ 所有按钮文本
- ✅ 标签和提示文字
- ✅ 错误和加载信息
- ✅ 资产类型显示

## 📁 核心文件

```
src/
├── locales.js              # 语言包（所有翻译文本）
├── components/
│   └── LangSwitcher.vue   # 语言切换按钮
├── composables/
│   └── useI18n.js         # 国际化逻辑
└── views/
    ├── Dashboard.vue      # ✓ 已完全国际化
    ├── Portfolio.vue      # 待完全国际化
    ├── Market.vue         # ✓ 部分国际化
    └── Analytics.vue      # ✓ 部分国际化
```

## 🔧 技术实现

### 1. 语言包结构
```javascript
export const messages = {
  zh: {
    dashboard: { title: '仪表盘' },
    common: { loading: '加载中...' }
  },
  en: {
    dashboard: { title: 'Dashboard' },
    common: { loading: 'Loading...' }
  }
};
```

### 2. 组件中使用
```vue
<script setup>
import { useI18n } from '../composables/useI18n';

const { lang, t } = useI18n();
</script>

<template>
  <h1>{{ t('dashboard.title') }}</h1>
</template>
```

### 3. 动态内容翻译
```javascript
function formatAssetType(type) {
  const keyMap = {
    'stock': 'assetType.stock',
    'bond': 'assetType.bond',
    'fund': 'assetType.fund',
    'cash': 'assetType.cash'
  };
  return t(keyMap[type]);
}
```

## 🎨 UI 设计

### 按钮样式
- **默认状态**: 白色背景，灰色边框
- **Hover 状态**: 渐变背景，轻微上浮效果
- **Active 状态**: 紫色渐变背景，阴影增强
- **尺寸**: 44x44px（移动端 40x40px）

### 动画效果
```css
transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
```

## 📊 翻译覆盖范围

| 页面 | 完成度 | 状态 |
|------|--------|------|
| Dashboard | 100% | ✅ 完成 |
| Market | 50% | 🔄 进行中 |
| Analytics | 50% | 🔄 进行中 |
| Portfolio | 0% | ⏳ 待开始 |

## 🚀 下一步

### 待完成的任务
1. 完成 Portfolio 页面的国际化
2. 完成 Market 页面的所有文本翻译
3. 完成 Analytics 页面的所有文本翻译
4. 添加更多语言的翻译（如繁体中文、日语等）

### 优化方向
- [ ] 添加翻译缺失检测
- [ ] 支持 RTL 语言（阿拉伯语等）
- [ ] 日期时间格式本地化
- [ ] 数字格式本地化（千分位、货币符号）
- [ ] 复数形式支持

## 💡 调试技巧

### 查看当前语言
```javascript
console.log('Current:', lang.value);
```

### 测试翻译
```javascript
t('your.translation.key');
```

### 监听变化
```javascript
window.addEventListener('lang-change', (e) => {
  console.log('Changed to:', e.detail.lang);
});
```

## ❓ 常见问题

**Q: 切换后某些文本没有变化？**
A: 检查该文本是否使用了 `t()` 函数，确保翻译键存在于 locales.js 中。

**Q: 如何添加新的翻译？**
A: 在 `locales.js` 的对应语言下添加键值对，保持中英文同步更新。

**Q: 语言设置保存在哪里？**
A: 使用 `localStorage`，键名为 `portfolio_lang`。

---

**最后更新**: 2026-04-01
