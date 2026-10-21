<script setup>
import { optionLabel } from "../modules/display";
import { visibleFields } from "../modules/recordVersions";
// 字段配置对应各接口DTO；只提交当前操作允许的字段，不回传整条查询对象。
defineProps({ model: Object, fields: Array });
</script>
<template>
  <div class="dto-grid">
    <el-form-item
      v-for="f in visibleFields(fields)"
      :key="f.key"
      :label="f.label"
    >
      <el-select
        v-if="f.options"
        v-model="model[f.key]"
        :disabled="f.disabled"
        :placeholder="f.placeholder || '请选择' + f.label"
        clearable
        :aria-label="f.label"
      >
        <el-option
          v-for="o in f.options"
          :key="String(o.value ?? o)"
          :value="o.value ?? o"
          :label="optionLabel(o)"
        />
      </el-select>
      <el-input-number
        v-else-if="f.type === 'number'"
        v-model="model[f.key]"
        :controls="false"
        :disabled="f.disabled"
        :placeholder="f.placeholder || '请输入' + f.label"
        :aria-label="f.label"
      />
      <el-date-picker
        v-else-if="f.type === 'date' || f.type === 'datetime'"
        v-model="model[f.key]"
        :disabled="f.disabled"
        :placeholder="f.placeholder || '请选择' + f.label"
        :type="f.type === 'date' ? 'date' : 'datetime'"
        :value-format="f.type === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss'"
        :aria-label="f.label"
      />
      <el-input
        v-else
        v-model="model[f.key]"
        :disabled="f.disabled"
        :placeholder="f.placeholder || '请输入' + f.label"
        :type="
          f.key === 'passwordHash'
            ? 'password'
            : f.type === 'textarea'
              ? 'textarea'
              : 'text'
        "
        :aria-label="f.label"
        clearable
      />
    </el-form-item>
  </div>
</template>
<style scoped>
.dto-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0 18px;
}
.dto-grid :deep(.el-select),
.dto-grid :deep(.el-input-number),
.dto-grid :deep(.el-date-editor) {
  width: 100%;
}
</style>
