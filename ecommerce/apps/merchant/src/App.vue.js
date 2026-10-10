import { ref } from 'vue';
const token = ref(localStorage.getItem('merchant_token') || '');
const username = ref('');
const password = ref('');
const info = ref(null);
const staff = ref([]);
const error = ref('');
const emp = ref({ username: '', password: '', display_name: '', role_code: 'MERCHANT_STAFF' });
async function login() { error.value = ''; const r = await fetch('/api/v1/merchant/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: username.value, password: password.value }) }); const j = await r.json(); if (!r.ok) {
    error.value = j.detail || '登录失败';
    return;
} ; token.value = j.data.access_token; localStorage.setItem('merchant_token', token.value); await load(); }
async function load() { const h = { Authorization: `Bearer ${token.value}` }; const r = await fetch('/api/v1/merchant/profile', { headers: h }); if (r.ok)
    info.value = (await r.json()).data;
else if (r.status === 401)
    logout(); const s = await fetch('/api/v1/merchant/employees', { headers: h }); if (s.ok)
    staff.value = (await s.json()).data; }
async function toggleEmployee(row) { const r = await fetch(`/api/v1/merchant/employees/${row.id}/status`, { method: 'PATCH', headers: { Authorization: `Bearer ${token.value}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' }) }); if (r.ok)
    await load();
else
    error.value = '更新员工状态失败'; }
async function addEmployee() { const r = await fetch('/api/v1/merchant/employees', { method: 'POST', headers: { Authorization: `Bearer ${token.value}`, 'Content-Type': 'application/json' }, body: JSON.stringify(emp.value) }); if (r.ok) {
    emp.value = { username: '', password: '', display_name: '', role_code: 'MERCHANT_STAFF' };
    await load();
}
else
    error.value = '创建员工失败'; }
function logout() { localStorage.removeItem('merchant_token'); token.value = ''; info.value = null; }
if (token.value)
    load();
const __VLS_ctx = {};
let __VLS_components;
let __VLS_intrinsics;
let __VLS_directives;
void __VLS_ctx, __VLS_components, __VLS_intrinsics, __VLS_directives;
// @ts-ignore
__VLS_withDotValue(token, {});
// @ts-ignore
__VLS_withDotValue(info, {});
// @ts-ignore
__VLS_withDotValue(emp, {});
if (!token.value) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.main, __VLS_intrinsics.main)({
        ...{ class: "login" },
    });
    /** @type {__VLS_StyleScopedClasses['login']} */ ;
    __VLS_asFunctionalElement1(__VLS_intrinsics.h1, __VLS_intrinsics.h1)({});
    let __VLS_0;
    /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
    elInput;
    // @ts-ignore
    const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
        // @ts-ignore
        modelValue: (__VLS_unwrap(username, {})), placeholder: "商家员工账号",
    }));
    const __VLS_2 = __VLS_1({
        modelValue: (__VLS_unwrap(username, {})),
        placeholder: "商家员工账号",
    }, ...__VLS_functionalComponentArgsRest(__VLS_1));
    let __VLS_5;
    /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
    elInput;
    // @ts-ignore
    const __VLS_6 = __VLS_asFunctionalComponent1(__VLS_5, new __VLS_5({
        // @ts-ignore
        ...{ 'onKeyup': {} }, modelValue: (__VLS_unwrap(password, {})), type: "password", placeholder: "密码",
    }));
    const __VLS_7 = __VLS_6({
        ...{ 'onKeyup': {} },
        modelValue: (__VLS_unwrap(password, {})),
        type: "password",
        placeholder: "密码",
    }, ...__VLS_functionalComponentArgsRest(__VLS_6));
    let __VLS_10;
    const __VLS_11 = {
        /** @type {typeof __VLS_10.keyup} */
        onKeyup: (login),
    };
    void __VLS_11;
    var __VLS_8;
    var __VLS_9;
    let __VLS_12;
    /** @ts-ignore @type { | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button'] | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button']} */
    elButton;
    // @ts-ignore
    const __VLS_13 = __VLS_asFunctionalComponent1(__VLS_12, new __VLS_12({
        // @ts-ignore
        ...{ 'onClick': {} }, type: "primary",
    }));
    const __VLS_14 = __VLS_13({
        ...{ 'onClick': {} },
        type: "primary",
    }, ...__VLS_functionalComponentArgsRest(__VLS_13));
    let __VLS_17;
    const __VLS_18 = {
        /** @type {typeof __VLS_17.click} */
        onClick: (login),
    };
    void __VLS_18;
    const { default: __VLS_19 } = __VLS_nonNull(__VLS_15.slots);
    // @ts-ignore
    [token, username, password,];
    var __VLS_15;
    var __VLS_16;
    __VLS_asFunctionalElement1(__VLS_intrinsics.p, __VLS_intrinsics.p)({});
    (__VLS_unwrap(error, {}));
}
else {
    let __VLS_20;
    /** @ts-ignore @type { | typeof __VLS_components.elContainer | typeof __VLS_components.ElContainer | typeof __VLS_components['el-container'] | typeof __VLS_components.elContainer | typeof __VLS_components.ElContainer | typeof __VLS_components['el-container']} */
    elContainer;
    // @ts-ignore
    const __VLS_21 = __VLS_asFunctionalComponent1(__VLS_20, new __VLS_20({
        // @ts-ignore
        ...{ class: "layout" },
    }));
    const __VLS_22 = __VLS_21({
        ...{ class: "layout" },
    }, ...__VLS_functionalComponentArgsRest(__VLS_21));
    /** @type {__VLS_StyleScopedClasses['layout']} */ ;
    const { default: __VLS_25 } = __VLS_nonNull(__VLS_23.slots);
    let __VLS_26;
    /** @ts-ignore @type { | typeof __VLS_components.elAside | typeof __VLS_components.ElAside | typeof __VLS_components['el-aside'] | typeof __VLS_components.elAside | typeof __VLS_components.ElAside | typeof __VLS_components['el-aside']} */
    elAside;
    // @ts-ignore
    const __VLS_27 = __VLS_asFunctionalComponent1(__VLS_26, new __VLS_26({
        // @ts-ignore
        width: "220px",
    }));
    const __VLS_28 = __VLS_27({
        width: "220px",
    }, ...__VLS_functionalComponentArgsRest(__VLS_27));
    const { default: __VLS_31 } = __VLS_nonNull(__VLS_29.slots);
    __VLS_asFunctionalElement1(__VLS_intrinsics.h2, __VLS_intrinsics.h2)({});
    let __VLS_32;
    /** @ts-ignore @type { | typeof __VLS_components.elMenu | typeof __VLS_components.ElMenu | typeof __VLS_components['el-menu'] | typeof __VLS_components.elMenu | typeof __VLS_components.ElMenu | typeof __VLS_components['el-menu']} */
    elMenu;
    // @ts-ignore
    const __VLS_33 = __VLS_asFunctionalComponent1(__VLS_32, new __VLS_32({
    // @ts-ignore
    }));
    const __VLS_34 = __VLS_33({}, ...__VLS_functionalComponentArgsRest(__VLS_33));
    const { default: __VLS_37 } = __VLS_nonNull(__VLS_35.slots);
    let __VLS_38;
    /** @ts-ignore @type { | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item'] | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item']} */
    elMenuItem;
    // @ts-ignore
    const __VLS_39 = __VLS_asFunctionalComponent1(__VLS_38, new __VLS_38({
        // @ts-ignore
        index: "1",
    }));
    const __VLS_40 = __VLS_39({
        index: "1",
    }, ...__VLS_functionalComponentArgsRest(__VLS_39));
    const { default: __VLS_43 } = __VLS_nonNull(__VLS_41.slots);
    // @ts-ignore
    [error,];
    var __VLS_41;
    let __VLS_44;
    /** @ts-ignore @type { | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item'] | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item']} */
    elMenuItem;
    // @ts-ignore
    const __VLS_45 = __VLS_asFunctionalComponent1(__VLS_44, new __VLS_44({
        // @ts-ignore
        index: "2",
    }));
    const __VLS_46 = __VLS_45({
        index: "2",
    }, ...__VLS_functionalComponentArgsRest(__VLS_45));
    const { default: __VLS_49 } = __VLS_nonNull(__VLS_47.slots);
    // @ts-ignore
    [];
    var __VLS_47;
    let __VLS_50;
    /** @ts-ignore @type { | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item'] | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item']} */
    elMenuItem;
    // @ts-ignore
    const __VLS_51 = __VLS_asFunctionalComponent1(__VLS_50, new __VLS_50({
        // @ts-ignore
        index: "3",
    }));
    const __VLS_52 = __VLS_51({
        index: "3",
    }, ...__VLS_functionalComponentArgsRest(__VLS_51));
    const { default: __VLS_55 } = __VLS_nonNull(__VLS_53.slots);
    // @ts-ignore
    [];
    var __VLS_53;
    // @ts-ignore
    [];
    var __VLS_35;
    let __VLS_56;
    /** @ts-ignore @type { | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button'] | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button']} */
    elButton;
    // @ts-ignore
    const __VLS_57 = __VLS_asFunctionalComponent1(__VLS_56, new __VLS_56({
        // @ts-ignore
        ...{ 'onClick': {} },
    }));
    const __VLS_58 = __VLS_57({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_57));
    let __VLS_61;
    const __VLS_62 = {
        /** @type {typeof __VLS_61.click} */
        onClick: (logout),
    };
    void __VLS_62;
    const { default: __VLS_63 } = __VLS_nonNull(__VLS_59.slots);
    // @ts-ignore
    [];
    var __VLS_59;
    var __VLS_60;
    // @ts-ignore
    [];
    var __VLS_29;
    let __VLS_64;
    /** @ts-ignore @type { | typeof __VLS_components.elContainer | typeof __VLS_components.ElContainer | typeof __VLS_components['el-container'] | typeof __VLS_components.elContainer | typeof __VLS_components.ElContainer | typeof __VLS_components['el-container']} */
    elContainer;
    // @ts-ignore
    const __VLS_65 = __VLS_asFunctionalComponent1(__VLS_64, new __VLS_64({
    // @ts-ignore
    }));
    const __VLS_66 = __VLS_65({}, ...__VLS_functionalComponentArgsRest(__VLS_65));
    const { default: __VLS_69 } = __VLS_nonNull(__VLS_67.slots);
    let __VLS_70;
    /** @ts-ignore @type { | typeof __VLS_components.elHeader | typeof __VLS_components.ElHeader | typeof __VLS_components['el-header'] | typeof __VLS_components.elHeader | typeof __VLS_components.ElHeader | typeof __VLS_components['el-header']} */
    elHeader;
    // @ts-ignore
    const __VLS_71 = __VLS_asFunctionalComponent1(__VLS_70, new __VLS_70({
    // @ts-ignore
    }));
    const __VLS_72 = __VLS_71({}, ...__VLS_functionalComponentArgsRest(__VLS_71));
    const { default: __VLS_75 } = __VLS_nonNull(__VLS_73.slots);
    (info.value?.name || '加载中');
    // @ts-ignore
    [info,];
    var __VLS_73;
    let __VLS_76;
    /** @ts-ignore @type { | typeof __VLS_components.elMain | typeof __VLS_components.ElMain | typeof __VLS_components['el-main'] | typeof __VLS_components.elMain | typeof __VLS_components.ElMain | typeof __VLS_components['el-main']} */
    elMain;
    // @ts-ignore
    const __VLS_77 = __VLS_asFunctionalComponent1(__VLS_76, new __VLS_76({
    // @ts-ignore
    }));
    const __VLS_78 = __VLS_77({}, ...__VLS_functionalComponentArgsRest(__VLS_77));
    const { default: __VLS_81 } = __VLS_nonNull(__VLS_79.slots);
    __VLS_asFunctionalElement1(__VLS_intrinsics.h1, __VLS_intrinsics.h1)({});
    if (info.value) {
        let __VLS_82;
        /** @ts-ignore @type { | typeof __VLS_components.elCard | typeof __VLS_components.ElCard | typeof __VLS_components['el-card'] | typeof __VLS_components.elCard | typeof __VLS_components.ElCard | typeof __VLS_components['el-card']} */
        elCard;
        // @ts-ignore
        const __VLS_83 = __VLS_asFunctionalComponent1(__VLS_82, new __VLS_82({
        // @ts-ignore
        }));
        const __VLS_84 = __VLS_83({}, ...__VLS_functionalComponentArgsRest(__VLS_83));
        const { default: __VLS_87 } = __VLS_nonNull(__VLS_85.slots);
        (info.value.id);
        __VLS_asFunctionalElement1(__VLS_intrinsics.br)({});
        (info.value.type);
        __VLS_asFunctionalElement1(__VLS_intrinsics.br)({});
        (info.value.status);
        // @ts-ignore
        [info, info, info, info,];
        var __VLS_85;
    }
    __VLS_asFunctionalElement1(__VLS_intrinsics.h2, __VLS_intrinsics.h2)({});
    let __VLS_88;
    /** @ts-ignore @type { | typeof __VLS_components.elForm | typeof __VLS_components.ElForm | typeof __VLS_components['el-form'] | typeof __VLS_components.elForm | typeof __VLS_components.ElForm | typeof __VLS_components['el-form']} */
    elForm;
    // @ts-ignore
    const __VLS_89 = __VLS_asFunctionalComponent1(__VLS_88, new __VLS_88({
        // @ts-ignore
        inline: true,
    }));
    const __VLS_90 = __VLS_89({
        inline: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_89));
    const { default: __VLS_93 } = __VLS_nonNull(__VLS_91.slots);
    let __VLS_94;
    /** @ts-ignore @type { | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item'] | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item']} */
    elFormItem;
    // @ts-ignore
    const __VLS_95 = __VLS_asFunctionalComponent1(__VLS_94, new __VLS_94({
        // @ts-ignore
        label: "账号",
    }));
    const __VLS_96 = __VLS_95({
        label: "账号",
    }, ...__VLS_functionalComponentArgsRest(__VLS_95));
    const { default: __VLS_99 } = __VLS_nonNull(__VLS_97.slots);
    let __VLS_100;
    /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
    elInput;
    // @ts-ignore
    const __VLS_101 = __VLS_asFunctionalComponent1(__VLS_100, new __VLS_100({
        // @ts-ignore
        modelValue: (emp.value.username),
    }));
    const __VLS_102 = __VLS_101({
        modelValue: (emp.value.username),
    }, ...__VLS_functionalComponentArgsRest(__VLS_101));
    // @ts-ignore
    [emp,];
    var __VLS_97;
    let __VLS_105;
    /** @ts-ignore @type { | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item'] | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item']} */
    elFormItem;
    // @ts-ignore
    const __VLS_106 = __VLS_asFunctionalComponent1(__VLS_105, new __VLS_105({
        // @ts-ignore
        label: "姓名",
    }));
    const __VLS_107 = __VLS_106({
        label: "姓名",
    }, ...__VLS_functionalComponentArgsRest(__VLS_106));
    const { default: __VLS_110 } = __VLS_nonNull(__VLS_108.slots);
    let __VLS_111;
    /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
    elInput;
    // @ts-ignore
    const __VLS_112 = __VLS_asFunctionalComponent1(__VLS_111, new __VLS_111({
        // @ts-ignore
        modelValue: (emp.value.display_name),
    }));
    const __VLS_113 = __VLS_112({
        modelValue: (emp.value.display_name),
    }, ...__VLS_functionalComponentArgsRest(__VLS_112));
    // @ts-ignore
    [emp,];
    var __VLS_108;
    let __VLS_116;
    /** @ts-ignore @type { | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item'] | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item']} */
    elFormItem;
    // @ts-ignore
    const __VLS_117 = __VLS_asFunctionalComponent1(__VLS_116, new __VLS_116({
        // @ts-ignore
        label: "初始密码",
    }));
    const __VLS_118 = __VLS_117({
        label: "初始密码",
    }, ...__VLS_functionalComponentArgsRest(__VLS_117));
    const { default: __VLS_121 } = __VLS_nonNull(__VLS_119.slots);
    let __VLS_122;
    /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
    elInput;
    // @ts-ignore
    const __VLS_123 = __VLS_asFunctionalComponent1(__VLS_122, new __VLS_122({
        // @ts-ignore
        modelValue: (emp.value.password), type: "password",
    }));
    const __VLS_124 = __VLS_123({
        modelValue: (emp.value.password),
        type: "password",
    }, ...__VLS_functionalComponentArgsRest(__VLS_123));
    // @ts-ignore
    [emp,];
    var __VLS_119;
    let __VLS_127;
    /** @ts-ignore @type { | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item'] | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item']} */
    elFormItem;
    // @ts-ignore
    const __VLS_128 = __VLS_asFunctionalComponent1(__VLS_127, new __VLS_127({
        // @ts-ignore
        label: "角色",
    }));
    const __VLS_129 = __VLS_128({
        label: "角色",
    }, ...__VLS_functionalComponentArgsRest(__VLS_128));
    const { default: __VLS_132 } = __VLS_nonNull(__VLS_130.slots);
    let __VLS_133;
    /** @ts-ignore @type { | typeof __VLS_components.elSelect | typeof __VLS_components.ElSelect | typeof __VLS_components['el-select'] | typeof __VLS_components.elSelect | typeof __VLS_components.ElSelect | typeof __VLS_components['el-select']} */
    elSelect;
    // @ts-ignore
    const __VLS_134 = __VLS_asFunctionalComponent1(__VLS_133, new __VLS_133({
        // @ts-ignore
        modelValue: (emp.value.role_code),
    }));
    const __VLS_135 = __VLS_134({
        modelValue: (emp.value.role_code),
    }, ...__VLS_functionalComponentArgsRest(__VLS_134));
    const { default: __VLS_138 } = __VLS_nonNull(__VLS_136.slots);
    let __VLS_139;
    /** @ts-ignore @type { | typeof __VLS_components.elOption | typeof __VLS_components.ElOption | typeof __VLS_components['el-option']} */
    elOption;
    // @ts-ignore
    const __VLS_140 = __VLS_asFunctionalComponent1(__VLS_139, new __VLS_139({
        // @ts-ignore
        label: "商家员工", value: "MERCHANT_STAFF",
    }));
    const __VLS_141 = __VLS_140({
        label: "商家员工",
        value: "MERCHANT_STAFF",
    }, ...__VLS_functionalComponentArgsRest(__VLS_140));
    let __VLS_144;
    /** @ts-ignore @type { | typeof __VLS_components.elOption | typeof __VLS_components.ElOption | typeof __VLS_components['el-option']} */
    elOption;
    // @ts-ignore
    const __VLS_145 = __VLS_asFunctionalComponent1(__VLS_144, new __VLS_144({
        // @ts-ignore
        label: "商家管理员", value: "MERCHANT_ADMIN",
    }));
    const __VLS_146 = __VLS_145({
        label: "商家管理员",
        value: "MERCHANT_ADMIN",
    }, ...__VLS_functionalComponentArgsRest(__VLS_145));
    // @ts-ignore
    [emp,];
    var __VLS_136;
    // @ts-ignore
    [];
    var __VLS_130;
    let __VLS_149;
    /** @ts-ignore @type { | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button'] | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button']} */
    elButton;
    // @ts-ignore
    const __VLS_150 = __VLS_asFunctionalComponent1(__VLS_149, new __VLS_149({
        // @ts-ignore
        ...{ 'onClick': {} }, type: "primary",
    }));
    const __VLS_151 = __VLS_150({
        ...{ 'onClick': {} },
        type: "primary",
    }, ...__VLS_functionalComponentArgsRest(__VLS_150));
    let __VLS_154;
    const __VLS_155 = {
        /** @type {typeof __VLS_154.click} */
        onClick: (addEmployee),
    };
    void __VLS_155;
    const { default: __VLS_156 } = __VLS_nonNull(__VLS_152.slots);
    // @ts-ignore
    [];
    var __VLS_152;
    var __VLS_153;
    // @ts-ignore
    [];
    var __VLS_91;
    __VLS_asFunctionalElement1(__VLS_intrinsics.p, __VLS_intrinsics.p)({
        ...{ class: "error" },
    });
    /** @type {__VLS_StyleScopedClasses['error']} */ ;
    (__VLS_unwrap(error, {}));
    let __VLS_157;
    /** @ts-ignore @type { | typeof __VLS_components.elTable | typeof __VLS_components.ElTable | typeof __VLS_components['el-table'] | typeof __VLS_components.elTable | typeof __VLS_components.ElTable | typeof __VLS_components['el-table']} */
    elTable;
    // @ts-ignore
    const __VLS_158 = __VLS_asFunctionalComponent1(__VLS_157, new __VLS_157({
        // @ts-ignore
        data: (__VLS_unwrap(staff, {})),
    }));
    const __VLS_159 = __VLS_158({
        data: (__VLS_unwrap(staff, {})),
    }, ...__VLS_functionalComponentArgsRest(__VLS_158));
    const { default: __VLS_162 } = __VLS_nonNull(__VLS_160.slots);
    let __VLS_163;
    /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
    elTableColumn;
    // @ts-ignore
    const __VLS_164 = __VLS_asFunctionalComponent1(__VLS_163, new __VLS_163({
        // @ts-ignore
        prop: "username", label: "账号",
    }));
    const __VLS_165 = __VLS_164({
        prop: "username",
        label: "账号",
    }, ...__VLS_functionalComponentArgsRest(__VLS_164));
    let __VLS_168;
    /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
    elTableColumn;
    // @ts-ignore
    const __VLS_169 = __VLS_asFunctionalComponent1(__VLS_168, new __VLS_168({
        // @ts-ignore
        prop: "display_name", label: "姓名",
    }));
    const __VLS_170 = __VLS_169({
        prop: "display_name",
        label: "姓名",
    }, ...__VLS_functionalComponentArgsRest(__VLS_169));
    let __VLS_173;
    /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
    elTableColumn;
    // @ts-ignore
    const __VLS_174 = __VLS_asFunctionalComponent1(__VLS_173, new __VLS_173({
        // @ts-ignore
        prop: "status", label: "状态",
    }));
    const __VLS_175 = __VLS_174({
        prop: "status",
        label: "状态",
    }, ...__VLS_functionalComponentArgsRest(__VLS_174));
    let __VLS_178;
    /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column'] | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
    elTableColumn;
    // @ts-ignore
    const __VLS_179 = __VLS_asFunctionalComponent1(__VLS_178, new __VLS_178({
        // @ts-ignore
        label: "操作",
    }));
    const __VLS_180 = __VLS_179({
        label: "操作",
    }, ...__VLS_functionalComponentArgsRest(__VLS_179));
    const { default: __VLS_183 } = __VLS_nonNull(__VLS_181.slots);
    {
        const { default: __VLS_184 } = __VLS_nonNull(__VLS_181.slots);
        const [s] = __VLS_vSlot(__VLS_nonNull(__VLS_184));
        let __VLS_185;
        /** @ts-ignore @type { | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button'] | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button']} */
        elButton;
        // @ts-ignore
        const __VLS_186 = __VLS_asFunctionalComponent1(__VLS_185, new __VLS_185({
            // @ts-ignore
            ...{ 'onClick': {} }, size: "small",
        }));
        const __VLS_187 = __VLS_186({
            ...{ 'onClick': {} },
            size: "small",
        }, ...__VLS_functionalComponentArgsRest(__VLS_186));
        let __VLS_190;
        const __VLS_191 = {
            /** @type {typeof __VLS_190.click} */
            onClick: // @ts-ignore
            (...[$event]) => {
                void $event;
                if (!!(!token.value))
                    throw 0;
                return (toggleEmployee(s.row));
                // @ts-ignore
                [error, staff,];
            },
        };
        void __VLS_191;
        const { default: __VLS_192 } = __VLS_nonNull(__VLS_188.slots);
        // @ts-ignore
        [];
        var __VLS_188;
        var __VLS_189;
        // @ts-ignore
        [];
    }
    // @ts-ignore
    [];
    var __VLS_181;
    // @ts-ignore
    [];
    var __VLS_160;
    __VLS_asFunctionalElement1(__VLS_intrinsics.p, __VLS_intrinsics.p)({});
    // @ts-ignore
    [];
    var __VLS_79;
    // @ts-ignore
    [];
    var __VLS_67;
    // @ts-ignore
    [];
    var __VLS_23;
}
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
