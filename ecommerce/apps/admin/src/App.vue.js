import { ref } from 'vue';
const token = ref(localStorage.getItem('admin_token') || '');
const username = ref('');
const password = ref('');
const page = ref('工作台');
const merchants = ref([]);
const users = ref([]);
const roles = ref([]);
const permissions = ref([]);
const logs = ref([]);
const profile = ref({});
const error = ref('');
const newMerchant = ref({ name: '', type: 'THIRD_PARTY' });
const newName = ref('');
const newPassword = ref('');
const newEmail = ref('');
const newStatus = ref('ACTIVE');
const selectedRole = ref('');
const newRoleCode = ref('');
const newRoleName = ref('');
const selectedPermissions = ref([]);
const oldPassword = ref('');
const password2 = ref('');
async function api(path, method = 'GET', body) { return fetch(path, { method, headers: { Authorization: `Bearer ${token.value}`, 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) }); }
async function login() { error.value = ''; const r = await fetch('/api/v1/admin/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: username.value, password: password.value }) }); const j = await r.json(); if (!r.ok) {
    error.value = j.error?.message || j.detail || '登录失败';
    return;
} ; token.value = j.data.access_token; localStorage.setItem('admin_token', token.value); await load(); }
async function load() {
    try {
        if (page.value === '商家档案') {
            const r = await api('/api/v1/admin/merchants');
            if (r.ok)
                merchants.value = (await r.json()).data;
        }
        if (page.value === '用户管理') {
            const r = await api('/api/v1/admin/users');
            if (r.ok)
                users.value = (await r.json()).data;
        }
        if (page.value === '角色权限') {
            const [a, b] = await Promise.all([api('/api/v1/admin/roles'), api('/api/v1/admin/permissions')]);
            if (a.ok)
                roles.value = (await a.json()).data;
            if (b.ok)
                permissions.value = (await b.json()).data;
        }
        if (page.value === '登录日志' || page.value === '操作日志') {
            const r = await api(page.value === '登录日志' ? '/api/v1/admin/login-logs' : '/api/v1/admin/audit-logs');
            if (r.ok)
                logs.value = (await r.json()).data;
        }
        if (page.value === '个人资料') {
            const r = await api('/api/v1/admin/me/profile');
            if (r.ok) {
                profile.value = (await r.json()).data;
                newName.value = profile.value.display_name;
            }
        }
    }
    catch (e) {
        error.value = '加载失败';
    }
}
function goto(p) { page.value = p; load(); }
async function createMerchant() { const r = await api('/api/v1/admin/merchants', 'POST', newMerchant.value); if (r.ok) {
    newMerchant.value = { name: '', type: 'THIRD_PARTY' };
    load();
}
else
    error.value = '创建商家失败'; }
async function toggleUser(u) { const status = u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'; const r = await api(`/api/v1/admin/users/${u.id}/status`, 'PATCH', { status }); if (r.ok)
    load(); }
async function selectRole(id) { selectedRole.value = id; const r = await api(`/api/v1/admin/roles/${id}/permissions`); selectedPermissions.value = r.ok ? (await r.json()).data.permission_ids : []; }
async function saveRole() { if (!selectedRole.value)
    return; const r = await api(`/api/v1/admin/roles/${selectedRole.value}/permissions`, 'PUT', { permission_ids: selectedPermissions.value }); if (!r.ok)
    error.value = '权限更新失败'; }
async function changePassword() { const r = await api('/api/v1/auth/change-password', 'POST', { old_password: oldPassword.value, new_password: password2.value }); if (r.ok) {
    error.value = '密码已修改，请重新登录';
    logout();
}
else
    error.value = '密码修改失败'; }
async function saveProfile() { const r = await api('/api/v1/admin/me/profile', 'PATCH', { display_name: newName.value }); if (r.ok)
    profile.value = (await r.json()).data;
else
    error.value = '保存失败'; }
async function logout() { await api('/api/v1/auth/logout', 'POST').catch(() => { }); localStorage.removeItem('admin_token'); token.value = ''; page.value = '工作台'; }
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
__VLS_withDotValue(page, {});
// @ts-ignore
__VLS_withDotValue(newMerchant, {});
// @ts-ignore
__VLS_withDotValue(profile, {});
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
        modelValue: (__VLS_unwrap(username, {})), placeholder: "管理员账号",
    }));
    const __VLS_2 = __VLS_1({
        modelValue: (__VLS_unwrap(username, {})),
        placeholder: "管理员账号",
    }, ...__VLS_functionalComponentArgsRest(__VLS_1));
    let __VLS_5;
    /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
    elInput;
    // @ts-ignore
    const __VLS_6 = __VLS_asFunctionalComponent1(__VLS_5, new __VLS_5({
        // @ts-ignore
        ...{ 'onKeyup': {} }, modelValue: (__VLS_unwrap(password, {})), type: "password", showPassword: true, placeholder: "密码",
    }));
    const __VLS_7 = __VLS_6({
        ...{ 'onKeyup': {} },
        modelValue: (__VLS_unwrap(password, {})),
        type: "password",
        showPassword: true,
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
        ...{ 'onSelect': {} }, defaultActive: (page.value),
    }));
    const __VLS_34 = __VLS_33({
        ...{ 'onSelect': {} },
        defaultActive: (page.value),
    }, ...__VLS_functionalComponentArgsRest(__VLS_33));
    let __VLS_37;
    const __VLS_38 = {
        /** @type {typeof __VLS_37.select} */
        onSelect: (goto),
    };
    void __VLS_38;
    const { default: __VLS_39 } = __VLS_nonNull(__VLS_35.slots);
    let __VLS_40;
    /** @ts-ignore @type { | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item'] | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item']} */
    elMenuItem;
    // @ts-ignore
    const __VLS_41 = __VLS_asFunctionalComponent1(__VLS_40, new __VLS_40({
        // @ts-ignore
        index: "工作台",
    }));
    const __VLS_42 = __VLS_41({
        index: "工作台",
    }, ...__VLS_functionalComponentArgsRest(__VLS_41));
    const { default: __VLS_45 } = __VLS_nonNull(__VLS_43.slots);
    // @ts-ignore
    [error, page,];
    var __VLS_43;
    let __VLS_46;
    /** @ts-ignore @type { | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item'] | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item']} */
    elMenuItem;
    // @ts-ignore
    const __VLS_47 = __VLS_asFunctionalComponent1(__VLS_46, new __VLS_46({
        // @ts-ignore
        index: "用户管理",
    }));
    const __VLS_48 = __VLS_47({
        index: "用户管理",
    }, ...__VLS_functionalComponentArgsRest(__VLS_47));
    const { default: __VLS_51 } = __VLS_nonNull(__VLS_49.slots);
    // @ts-ignore
    [];
    var __VLS_49;
    let __VLS_52;
    /** @ts-ignore @type { | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item'] | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item']} */
    elMenuItem;
    // @ts-ignore
    const __VLS_53 = __VLS_asFunctionalComponent1(__VLS_52, new __VLS_52({
        // @ts-ignore
        index: "角色权限",
    }));
    const __VLS_54 = __VLS_53({
        index: "角色权限",
    }, ...__VLS_functionalComponentArgsRest(__VLS_53));
    const { default: __VLS_57 } = __VLS_nonNull(__VLS_55.slots);
    // @ts-ignore
    [];
    var __VLS_55;
    let __VLS_58;
    /** @ts-ignore @type { | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item'] | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item']} */
    elMenuItem;
    // @ts-ignore
    const __VLS_59 = __VLS_asFunctionalComponent1(__VLS_58, new __VLS_58({
        // @ts-ignore
        index: "商家档案",
    }));
    const __VLS_60 = __VLS_59({
        index: "商家档案",
    }, ...__VLS_functionalComponentArgsRest(__VLS_59));
    const { default: __VLS_63 } = __VLS_nonNull(__VLS_61.slots);
    // @ts-ignore
    [];
    var __VLS_61;
    let __VLS_64;
    /** @ts-ignore @type { | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item'] | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item']} */
    elMenuItem;
    // @ts-ignore
    const __VLS_65 = __VLS_asFunctionalComponent1(__VLS_64, new __VLS_64({
        // @ts-ignore
        index: "登录日志",
    }));
    const __VLS_66 = __VLS_65({
        index: "登录日志",
    }, ...__VLS_functionalComponentArgsRest(__VLS_65));
    const { default: __VLS_69 } = __VLS_nonNull(__VLS_67.slots);
    // @ts-ignore
    [];
    var __VLS_67;
    let __VLS_70;
    /** @ts-ignore @type { | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item'] | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item']} */
    elMenuItem;
    // @ts-ignore
    const __VLS_71 = __VLS_asFunctionalComponent1(__VLS_70, new __VLS_70({
        // @ts-ignore
        index: "操作日志",
    }));
    const __VLS_72 = __VLS_71({
        index: "操作日志",
    }, ...__VLS_functionalComponentArgsRest(__VLS_71));
    const { default: __VLS_75 } = __VLS_nonNull(__VLS_73.slots);
    // @ts-ignore
    [];
    var __VLS_73;
    let __VLS_76;
    /** @ts-ignore @type { | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item'] | typeof __VLS_components.elMenuItem | typeof __VLS_components.ElMenuItem | typeof __VLS_components['el-menu-item']} */
    elMenuItem;
    // @ts-ignore
    const __VLS_77 = __VLS_asFunctionalComponent1(__VLS_76, new __VLS_76({
        // @ts-ignore
        index: "个人资料",
    }));
    const __VLS_78 = __VLS_77({
        index: "个人资料",
    }, ...__VLS_functionalComponentArgsRest(__VLS_77));
    const { default: __VLS_81 } = __VLS_nonNull(__VLS_79.slots);
    // @ts-ignore
    [];
    var __VLS_79;
    // @ts-ignore
    [];
    var __VLS_35;
    var __VLS_36;
    let __VLS_82;
    /** @ts-ignore @type { | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button'] | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button']} */
    elButton;
    // @ts-ignore
    const __VLS_83 = __VLS_asFunctionalComponent1(__VLS_82, new __VLS_82({
        // @ts-ignore
        ...{ 'onClick': {} },
    }));
    const __VLS_84 = __VLS_83({
        ...{ 'onClick': {} },
    }, ...__VLS_functionalComponentArgsRest(__VLS_83));
    let __VLS_87;
    const __VLS_88 = {
        /** @type {typeof __VLS_87.click} */
        onClick: (logout),
    };
    void __VLS_88;
    const { default: __VLS_89 } = __VLS_nonNull(__VLS_85.slots);
    // @ts-ignore
    [];
    var __VLS_85;
    var __VLS_86;
    // @ts-ignore
    [];
    var __VLS_29;
    let __VLS_90;
    /** @ts-ignore @type { | typeof __VLS_components.elContainer | typeof __VLS_components.ElContainer | typeof __VLS_components['el-container'] | typeof __VLS_components.elContainer | typeof __VLS_components.ElContainer | typeof __VLS_components['el-container']} */
    elContainer;
    // @ts-ignore
    const __VLS_91 = __VLS_asFunctionalComponent1(__VLS_90, new __VLS_90({
    // @ts-ignore
    }));
    const __VLS_92 = __VLS_91({}, ...__VLS_functionalComponentArgsRest(__VLS_91));
    const { default: __VLS_95 } = __VLS_nonNull(__VLS_93.slots);
    let __VLS_96;
    /** @ts-ignore @type { | typeof __VLS_components.elHeader | typeof __VLS_components.ElHeader | typeof __VLS_components['el-header'] | typeof __VLS_components.elHeader | typeof __VLS_components.ElHeader | typeof __VLS_components['el-header']} */
    elHeader;
    // @ts-ignore
    const __VLS_97 = __VLS_asFunctionalComponent1(__VLS_96, new __VLS_96({
    // @ts-ignore
    }));
    const __VLS_98 = __VLS_97({}, ...__VLS_functionalComponentArgsRest(__VLS_97));
    const { default: __VLS_101 } = __VLS_nonNull(__VLS_99.slots);
    (page.value);
    __VLS_asFunctionalElement1(__VLS_intrinsics.span, __VLS_intrinsics.span)({
        ...{ class: "right" },
    });
    /** @type {__VLS_StyleScopedClasses['right']} */ ;
    // @ts-ignore
    [page,];
    var __VLS_99;
    let __VLS_102;
    /** @ts-ignore @type { | typeof __VLS_components.elMain | typeof __VLS_components.ElMain | typeof __VLS_components['el-main'] | typeof __VLS_components.elMain | typeof __VLS_components.ElMain | typeof __VLS_components['el-main']} */
    elMain;
    // @ts-ignore
    const __VLS_103 = __VLS_asFunctionalComponent1(__VLS_102, new __VLS_102({
    // @ts-ignore
    }));
    const __VLS_104 = __VLS_103({}, ...__VLS_functionalComponentArgsRest(__VLS_103));
    const { default: __VLS_107 } = __VLS_nonNull(__VLS_105.slots);
    __VLS_asFunctionalElement1(__VLS_intrinsics.h1, __VLS_intrinsics.h1)({});
    (page.value);
    __VLS_asFunctionalElement1(__VLS_intrinsics.p, __VLS_intrinsics.p)({
        ...{ class: "error" },
    });
    /** @type {__VLS_StyleScopedClasses['error']} */ ;
    (__VLS_unwrap(error, {}));
    if (page.value === '工作台') {
        let __VLS_108;
        /** @ts-ignore @type { | typeof __VLS_components.elCard | typeof __VLS_components.ElCard | typeof __VLS_components['el-card'] | typeof __VLS_components.elCard | typeof __VLS_components.ElCard | typeof __VLS_components['el-card']} */
        elCard;
        // @ts-ignore
        const __VLS_109 = __VLS_asFunctionalComponent1(__VLS_108, new __VLS_108({
        // @ts-ignore
        }));
        const __VLS_110 = __VLS_109({}, ...__VLS_functionalComponentArgsRest(__VLS_109));
        const { default: __VLS_113 } = __VLS_nonNull(__VLS_111.slots);
        // @ts-ignore
        [error, page, page,];
        var __VLS_111;
    }
    else if (page.value === '商家档案') {
        let __VLS_114;
        /** @ts-ignore @type { | typeof __VLS_components.elForm | typeof __VLS_components.ElForm | typeof __VLS_components['el-form'] | typeof __VLS_components.elForm | typeof __VLS_components.ElForm | typeof __VLS_components['el-form']} */
        elForm;
        // @ts-ignore
        const __VLS_115 = __VLS_asFunctionalComponent1(__VLS_114, new __VLS_114({
            // @ts-ignore
            inline: true,
        }));
        const __VLS_116 = __VLS_115({
            inline: true,
        }, ...__VLS_functionalComponentArgsRest(__VLS_115));
        const { default: __VLS_119 } = __VLS_nonNull(__VLS_117.slots);
        let __VLS_120;
        /** @ts-ignore @type { | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item'] | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item']} */
        elFormItem;
        // @ts-ignore
        const __VLS_121 = __VLS_asFunctionalComponent1(__VLS_120, new __VLS_120({
            // @ts-ignore
            label: "商家名称",
        }));
        const __VLS_122 = __VLS_121({
            label: "商家名称",
        }, ...__VLS_functionalComponentArgsRest(__VLS_121));
        const { default: __VLS_125 } = __VLS_nonNull(__VLS_123.slots);
        let __VLS_126;
        /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
        elInput;
        // @ts-ignore
        const __VLS_127 = __VLS_asFunctionalComponent1(__VLS_126, new __VLS_126({
            // @ts-ignore
            modelValue: (newMerchant.value.name),
        }));
        const __VLS_128 = __VLS_127({
            modelValue: (newMerchant.value.name),
        }, ...__VLS_functionalComponentArgsRest(__VLS_127));
        // @ts-ignore
        [page, newMerchant,];
        var __VLS_123;
        let __VLS_131;
        /** @ts-ignore @type { | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item'] | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item']} */
        elFormItem;
        // @ts-ignore
        const __VLS_132 = __VLS_asFunctionalComponent1(__VLS_131, new __VLS_131({
            // @ts-ignore
            label: "类型",
        }));
        const __VLS_133 = __VLS_132({
            label: "类型",
        }, ...__VLS_functionalComponentArgsRest(__VLS_132));
        const { default: __VLS_136 } = __VLS_nonNull(__VLS_134.slots);
        let __VLS_137;
        /** @ts-ignore @type { | typeof __VLS_components.elSelect | typeof __VLS_components.ElSelect | typeof __VLS_components['el-select'] | typeof __VLS_components.elSelect | typeof __VLS_components.ElSelect | typeof __VLS_components['el-select']} */
        elSelect;
        // @ts-ignore
        const __VLS_138 = __VLS_asFunctionalComponent1(__VLS_137, new __VLS_137({
            // @ts-ignore
            modelValue: (newMerchant.value.type),
        }));
        const __VLS_139 = __VLS_138({
            modelValue: (newMerchant.value.type),
        }, ...__VLS_functionalComponentArgsRest(__VLS_138));
        const { default: __VLS_142 } = __VLS_nonNull(__VLS_140.slots);
        let __VLS_143;
        /** @ts-ignore @type { | typeof __VLS_components.elOption | typeof __VLS_components.ElOption | typeof __VLS_components['el-option']} */
        elOption;
        // @ts-ignore
        const __VLS_144 = __VLS_asFunctionalComponent1(__VLS_143, new __VLS_143({
            // @ts-ignore
            label: "第三方商家", value: "THIRD_PARTY",
        }));
        const __VLS_145 = __VLS_144({
            label: "第三方商家",
            value: "THIRD_PARTY",
        }, ...__VLS_functionalComponentArgsRest(__VLS_144));
        let __VLS_148;
        /** @ts-ignore @type { | typeof __VLS_components.elOption | typeof __VLS_components.ElOption | typeof __VLS_components['el-option']} */
        elOption;
        // @ts-ignore
        const __VLS_149 = __VLS_asFunctionalComponent1(__VLS_148, new __VLS_148({
            // @ts-ignore
            label: "平台自营", value: "PLATFORM_SELF",
        }));
        const __VLS_150 = __VLS_149({
            label: "平台自营",
            value: "PLATFORM_SELF",
        }, ...__VLS_functionalComponentArgsRest(__VLS_149));
        // @ts-ignore
        [newMerchant,];
        var __VLS_140;
        // @ts-ignore
        [];
        var __VLS_134;
        let __VLS_153;
        /** @ts-ignore @type { | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button'] | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button']} */
        elButton;
        // @ts-ignore
        const __VLS_154 = __VLS_asFunctionalComponent1(__VLS_153, new __VLS_153({
            // @ts-ignore
            ...{ 'onClick': {} }, type: "primary",
        }));
        const __VLS_155 = __VLS_154({
            ...{ 'onClick': {} },
            type: "primary",
        }, ...__VLS_functionalComponentArgsRest(__VLS_154));
        let __VLS_158;
        const __VLS_159 = {
            /** @type {typeof __VLS_158.click} */
            onClick: (createMerchant),
        };
        void __VLS_159;
        const { default: __VLS_160 } = __VLS_nonNull(__VLS_156.slots);
        // @ts-ignore
        [];
        var __VLS_156;
        var __VLS_157;
        // @ts-ignore
        [];
        var __VLS_117;
        let __VLS_161;
        /** @ts-ignore @type { | typeof __VLS_components.elTable | typeof __VLS_components.ElTable | typeof __VLS_components['el-table'] | typeof __VLS_components.elTable | typeof __VLS_components.ElTable | typeof __VLS_components['el-table']} */
        elTable;
        // @ts-ignore
        const __VLS_162 = __VLS_asFunctionalComponent1(__VLS_161, new __VLS_161({
            // @ts-ignore
            data: (__VLS_unwrap(merchants, {})),
        }));
        const __VLS_163 = __VLS_162({
            data: (__VLS_unwrap(merchants, {})),
        }, ...__VLS_functionalComponentArgsRest(__VLS_162));
        const { default: __VLS_166 } = __VLS_nonNull(__VLS_164.slots);
        let __VLS_167;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_168 = __VLS_asFunctionalComponent1(__VLS_167, new __VLS_167({
            // @ts-ignore
            prop: "name", label: "商家名称",
        }));
        const __VLS_169 = __VLS_168({
            prop: "name",
            label: "商家名称",
        }, ...__VLS_functionalComponentArgsRest(__VLS_168));
        let __VLS_172;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_173 = __VLS_asFunctionalComponent1(__VLS_172, new __VLS_172({
            // @ts-ignore
            prop: "type", label: "类型",
        }));
        const __VLS_174 = __VLS_173({
            prop: "type",
            label: "类型",
        }, ...__VLS_functionalComponentArgsRest(__VLS_173));
        let __VLS_177;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_178 = __VLS_asFunctionalComponent1(__VLS_177, new __VLS_177({
            // @ts-ignore
            prop: "status", label: "状态",
        }));
        const __VLS_179 = __VLS_178({
            prop: "status",
            label: "状态",
        }, ...__VLS_functionalComponentArgsRest(__VLS_178));
        let __VLS_182;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_183 = __VLS_asFunctionalComponent1(__VLS_182, new __VLS_182({
            // @ts-ignore
            prop: "review_status", label: "审核状态",
        }));
        const __VLS_184 = __VLS_183({
            prop: "review_status",
            label: "审核状态",
        }, ...__VLS_functionalComponentArgsRest(__VLS_183));
        // @ts-ignore
        [merchants,];
        var __VLS_164;
    }
    else if (page.value === '用户管理') {
        let __VLS_187;
        /** @ts-ignore @type { | typeof __VLS_components.elTable | typeof __VLS_components.ElTable | typeof __VLS_components['el-table'] | typeof __VLS_components.elTable | typeof __VLS_components.ElTable | typeof __VLS_components['el-table']} */
        elTable;
        // @ts-ignore
        const __VLS_188 = __VLS_asFunctionalComponent1(__VLS_187, new __VLS_187({
            // @ts-ignore
            data: (__VLS_unwrap(users, {})),
        }));
        const __VLS_189 = __VLS_188({
            data: (__VLS_unwrap(users, {})),
        }, ...__VLS_functionalComponentArgsRest(__VLS_188));
        const { default: __VLS_192 } = __VLS_nonNull(__VLS_190.slots);
        let __VLS_193;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_194 = __VLS_asFunctionalComponent1(__VLS_193, new __VLS_193({
            // @ts-ignore
            prop: "email", label: "邮箱",
        }));
        const __VLS_195 = __VLS_194({
            prop: "email",
            label: "邮箱",
        }, ...__VLS_functionalComponentArgsRest(__VLS_194));
        let __VLS_198;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_199 = __VLS_asFunctionalComponent1(__VLS_198, new __VLS_198({
            // @ts-ignore
            prop: "display_name", label: "昵称",
        }));
        const __VLS_200 = __VLS_199({
            prop: "display_name",
            label: "昵称",
        }, ...__VLS_functionalComponentArgsRest(__VLS_199));
        let __VLS_203;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_204 = __VLS_asFunctionalComponent1(__VLS_203, new __VLS_203({
            // @ts-ignore
            prop: "status", label: "状态",
        }));
        const __VLS_205 = __VLS_204({
            prop: "status",
            label: "状态",
        }, ...__VLS_functionalComponentArgsRest(__VLS_204));
        let __VLS_208;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column'] | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_209 = __VLS_asFunctionalComponent1(__VLS_208, new __VLS_208({
            // @ts-ignore
            label: "操作",
        }));
        const __VLS_210 = __VLS_209({
            label: "操作",
        }, ...__VLS_functionalComponentArgsRest(__VLS_209));
        const { default: __VLS_213 } = __VLS_nonNull(__VLS_211.slots);
        {
            const { default: __VLS_214 } = __VLS_nonNull(__VLS_211.slots);
            const [s] = __VLS_vSlot(__VLS_nonNull(__VLS_214));
            let __VLS_215;
            /** @ts-ignore @type { | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button'] | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button']} */
            elButton;
            // @ts-ignore
            const __VLS_216 = __VLS_asFunctionalComponent1(__VLS_215, new __VLS_215({
                // @ts-ignore
                ...{ 'onClick': {} }, size: "small",
            }));
            const __VLS_217 = __VLS_216({
                ...{ 'onClick': {} },
                size: "small",
            }, ...__VLS_functionalComponentArgsRest(__VLS_216));
            let __VLS_220;
            const __VLS_221 = {
                /** @type {typeof __VLS_220.click} */
                onClick: // @ts-ignore
                (...[$event]) => {
                    void $event;
                    if (!!(!token.value))
                        throw 0;
                    if (!!(page.value === '工作台'))
                        throw 0;
                    if (!!(page.value === '商家档案'))
                        throw 0;
                    if (!(page.value === '用户管理'))
                        throw 0;
                    return (toggleUser(s.row));
                    // @ts-ignore
                    [page, users,];
                },
            };
            void __VLS_221;
            const { default: __VLS_222 } = __VLS_nonNull(__VLS_218.slots);
            (s.row.status === 'ACTIVE' ? '禁用' : '启用');
            // @ts-ignore
            [];
            var __VLS_218;
            var __VLS_219;
            // @ts-ignore
            [];
        }
        // @ts-ignore
        [];
        var __VLS_211;
        // @ts-ignore
        [];
        var __VLS_190;
    }
    else if (page.value === '角色权限') {
        let __VLS_223;
        /** @ts-ignore @type { | typeof __VLS_components.elSelect | typeof __VLS_components.ElSelect | typeof __VLS_components['el-select'] | typeof __VLS_components.elSelect | typeof __VLS_components.ElSelect | typeof __VLS_components['el-select']} */
        elSelect;
        // @ts-ignore
        const __VLS_224 = __VLS_asFunctionalComponent1(__VLS_223, new __VLS_223({
            // @ts-ignore
            ...{ 'onChange': {} }, modelValue: (__VLS_unwrap(selectedRole, {})), placeholder: "选择角色",
        }));
        const __VLS_225 = __VLS_224({
            ...{ 'onChange': {} },
            modelValue: (__VLS_unwrap(selectedRole, {})),
            placeholder: "选择角色",
        }, ...__VLS_functionalComponentArgsRest(__VLS_224));
        let __VLS_228;
        const __VLS_229 = {
            /** @type {typeof __VLS_228.change} */
            onChange: (selectRole),
        };
        void __VLS_229;
        const { default: __VLS_230 } = __VLS_nonNull(__VLS_226.slots);
        const __VLS_231 = __VLS_tryAsConstant((__VLS_unwrap(roles, {})));
        for (const [r] of __VLS_vFor(__VLS_nonNull(__VLS_231))) {
            let __VLS_232;
            /** @ts-ignore @type { | typeof __VLS_components.elOption | typeof __VLS_components.ElOption | typeof __VLS_components['el-option']} */
            elOption;
            // @ts-ignore
            const __VLS_233 = __VLS_asFunctionalComponent1(__VLS_232, new __VLS_232({
                // @ts-ignore
                key: (r.id), label: (r.name + ' (' + r.code + ')'), value: (r.id),
            }));
            const __VLS_234 = __VLS_233({
                key: (r.id),
                label: (r.name + ' (' + r.code + ')'),
                value: (r.id),
            }, ...__VLS_functionalComponentArgsRest(__VLS_233));
            // @ts-ignore
            [page, selectedRole, roles,];
        }
        // @ts-ignore
        [];
        var __VLS_226;
        var __VLS_227;
        let __VLS_237;
        /** @ts-ignore @type { | typeof __VLS_components.elCheckboxGroup | typeof __VLS_components.ElCheckboxGroup | typeof __VLS_components['el-checkbox-group'] | typeof __VLS_components.elCheckboxGroup | typeof __VLS_components.ElCheckboxGroup | typeof __VLS_components['el-checkbox-group']} */
        elCheckboxGroup;
        // @ts-ignore
        const __VLS_238 = __VLS_asFunctionalComponent1(__VLS_237, new __VLS_237({
            // @ts-ignore
            modelValue: (__VLS_unwrap(selectedPermissions, {})),
        }));
        const __VLS_239 = __VLS_238({
            modelValue: (__VLS_unwrap(selectedPermissions, {})),
        }, ...__VLS_functionalComponentArgsRest(__VLS_238));
        const { default: __VLS_242 } = __VLS_nonNull(__VLS_240.slots);
        const __VLS_243 = __VLS_tryAsConstant((__VLS_unwrap(permissions, {})));
        for (const [p] of __VLS_vFor(__VLS_nonNull(__VLS_243))) {
            let __VLS_244;
            /** @ts-ignore @type { | typeof __VLS_components.elCheckbox | typeof __VLS_components.ElCheckbox | typeof __VLS_components['el-checkbox'] | typeof __VLS_components.elCheckbox | typeof __VLS_components.ElCheckbox | typeof __VLS_components['el-checkbox']} */
            elCheckbox;
            // @ts-ignore
            const __VLS_245 = __VLS_asFunctionalComponent1(__VLS_244, new __VLS_244({
                // @ts-ignore
                key: (p.id), label: (p.id),
            }));
            const __VLS_246 = __VLS_245({
                key: (p.id),
                label: (p.id),
            }, ...__VLS_functionalComponentArgsRest(__VLS_245));
            const { default: __VLS_249 } = __VLS_nonNull(__VLS_247.slots);
            (p.name);
            // @ts-ignore
            [selectedPermissions, permissions,];
            var __VLS_247;
            // @ts-ignore
            [];
        }
        // @ts-ignore
        [];
        var __VLS_240;
        let __VLS_250;
        /** @ts-ignore @type { | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button'] | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button']} */
        elButton;
        // @ts-ignore
        const __VLS_251 = __VLS_asFunctionalComponent1(__VLS_250, new __VLS_250({
            // @ts-ignore
            ...{ 'onClick': {} }, type: "primary",
        }));
        const __VLS_252 = __VLS_251({
            ...{ 'onClick': {} },
            type: "primary",
        }, ...__VLS_functionalComponentArgsRest(__VLS_251));
        let __VLS_255;
        const __VLS_256 = {
            /** @type {typeof __VLS_255.click} */
            onClick: (saveRole),
        };
        void __VLS_256;
        const { default: __VLS_257 } = __VLS_nonNull(__VLS_253.slots);
        // @ts-ignore
        [];
        var __VLS_253;
        var __VLS_254;
        let __VLS_258;
        /** @ts-ignore @type { | typeof __VLS_components.elDivider | typeof __VLS_components.ElDivider | typeof __VLS_components['el-divider']} */
        elDivider;
        // @ts-ignore
        const __VLS_259 = __VLS_asFunctionalComponent1(__VLS_258, new __VLS_258({
        // @ts-ignore
        }));
        const __VLS_260 = __VLS_259({}, ...__VLS_functionalComponentArgsRest(__VLS_259));
        let __VLS_263;
        /** @ts-ignore @type { | typeof __VLS_components.elTable | typeof __VLS_components.ElTable | typeof __VLS_components['el-table'] | typeof __VLS_components.elTable | typeof __VLS_components.ElTable | typeof __VLS_components['el-table']} */
        elTable;
        // @ts-ignore
        const __VLS_264 = __VLS_asFunctionalComponent1(__VLS_263, new __VLS_263({
            // @ts-ignore
            data: (__VLS_unwrap(roles, {})),
        }));
        const __VLS_265 = __VLS_264({
            data: (__VLS_unwrap(roles, {})),
        }, ...__VLS_functionalComponentArgsRest(__VLS_264));
        const { default: __VLS_268 } = __VLS_nonNull(__VLS_266.slots);
        let __VLS_269;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_270 = __VLS_asFunctionalComponent1(__VLS_269, new __VLS_269({
            // @ts-ignore
            prop: "name", label: "角色",
        }));
        const __VLS_271 = __VLS_270({
            prop: "name",
            label: "角色",
        }, ...__VLS_functionalComponentArgsRest(__VLS_270));
        let __VLS_274;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_275 = __VLS_asFunctionalComponent1(__VLS_274, new __VLS_274({
            // @ts-ignore
            prop: "scope", label: "作用域",
        }));
        const __VLS_276 = __VLS_275({
            prop: "scope",
            label: "作用域",
        }, ...__VLS_functionalComponentArgsRest(__VLS_275));
        let __VLS_279;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_280 = __VLS_asFunctionalComponent1(__VLS_279, new __VLS_279({
            // @ts-ignore
            prop: "merchant_id", label: "商家范围",
        }));
        const __VLS_281 = __VLS_280({
            prop: "merchant_id",
            label: "商家范围",
        }, ...__VLS_functionalComponentArgsRest(__VLS_280));
        // @ts-ignore
        [roles,];
        var __VLS_266;
    }
    else if (page.value === '登录日志' || page.value === '操作日志') {
        let __VLS_284;
        /** @ts-ignore @type { | typeof __VLS_components.elTable | typeof __VLS_components.ElTable | typeof __VLS_components['el-table'] | typeof __VLS_components.elTable | typeof __VLS_components.ElTable | typeof __VLS_components['el-table']} */
        elTable;
        // @ts-ignore
        const __VLS_285 = __VLS_asFunctionalComponent1(__VLS_284, new __VLS_284({
            // @ts-ignore
            data: (__VLS_unwrap(logs, {})),
        }));
        const __VLS_286 = __VLS_285({
            data: (__VLS_unwrap(logs, {})),
        }, ...__VLS_functionalComponentArgsRest(__VLS_285));
        const { default: __VLS_289 } = __VLS_nonNull(__VLS_287.slots);
        let __VLS_290;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_291 = __VLS_asFunctionalComponent1(__VLS_290, new __VLS_290({
            // @ts-ignore
            prop: "action", label: "事件",
        }));
        const __VLS_292 = __VLS_291({
            prop: "action",
            label: "事件",
        }, ...__VLS_functionalComponentArgsRest(__VLS_291));
        let __VLS_295;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_296 = __VLS_asFunctionalComponent1(__VLS_295, new __VLS_295({
            // @ts-ignore
            prop: "actor_id", label: "操作者",
        }));
        const __VLS_297 = __VLS_296({
            prop: "actor_id",
            label: "操作者",
        }, ...__VLS_functionalComponentArgsRest(__VLS_296));
        let __VLS_300;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_301 = __VLS_asFunctionalComponent1(__VLS_300, new __VLS_300({
            // @ts-ignore
            prop: "success", label: "成功",
        }));
        const __VLS_302 = __VLS_301({
            prop: "success",
            label: "成功",
        }, ...__VLS_functionalComponentArgsRest(__VLS_301));
        let __VLS_305;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_306 = __VLS_asFunctionalComponent1(__VLS_305, new __VLS_305({
            // @ts-ignore
            prop: "request_id", label: "请求 ID",
        }));
        const __VLS_307 = __VLS_306({
            prop: "request_id",
            label: "请求 ID",
        }, ...__VLS_functionalComponentArgsRest(__VLS_306));
        let __VLS_310;
        /** @ts-ignore @type { | typeof __VLS_components.elTableColumn | typeof __VLS_components.ElTableColumn | typeof __VLS_components['el-table-column']} */
        elTableColumn;
        // @ts-ignore
        const __VLS_311 = __VLS_asFunctionalComponent1(__VLS_310, new __VLS_310({
            // @ts-ignore
            prop: "created_at", label: "时间",
        }));
        const __VLS_312 = __VLS_311({
            prop: "created_at",
            label: "时间",
        }, ...__VLS_functionalComponentArgsRest(__VLS_311));
        // @ts-ignore
        [page, page, logs,];
        var __VLS_287;
    }
    else if (page.value === '个人资料') {
        let __VLS_315;
        /** @ts-ignore @type { | typeof __VLS_components.elForm | typeof __VLS_components.ElForm | typeof __VLS_components['el-form'] | typeof __VLS_components.elForm | typeof __VLS_components.ElForm | typeof __VLS_components['el-form']} */
        elForm;
        // @ts-ignore
        const __VLS_316 = __VLS_asFunctionalComponent1(__VLS_315, new __VLS_315({
            // @ts-ignore
            labelWidth: "120px",
        }));
        const __VLS_317 = __VLS_316({
            labelWidth: "120px",
        }, ...__VLS_functionalComponentArgsRest(__VLS_316));
        const { default: __VLS_320 } = __VLS_nonNull(__VLS_318.slots);
        let __VLS_321;
        /** @ts-ignore @type { | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item'] | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item']} */
        elFormItem;
        // @ts-ignore
        const __VLS_322 = __VLS_asFunctionalComponent1(__VLS_321, new __VLS_321({
            // @ts-ignore
            label: "用户名",
        }));
        const __VLS_323 = __VLS_322({
            label: "用户名",
        }, ...__VLS_functionalComponentArgsRest(__VLS_322));
        const { default: __VLS_326 } = __VLS_nonNull(__VLS_324.slots);
        let __VLS_327;
        /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
        elInput;
        // @ts-ignore
        const __VLS_328 = __VLS_asFunctionalComponent1(__VLS_327, new __VLS_327({
            // @ts-ignore
            modelValue: (profile.value.username), disabled: true,
        }));
        const __VLS_329 = __VLS_328({
            modelValue: (profile.value.username),
            disabled: true,
        }, ...__VLS_functionalComponentArgsRest(__VLS_328));
        // @ts-ignore
        [page, profile,];
        var __VLS_324;
        let __VLS_332;
        /** @ts-ignore @type { | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item'] | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item']} */
        elFormItem;
        // @ts-ignore
        const __VLS_333 = __VLS_asFunctionalComponent1(__VLS_332, new __VLS_332({
            // @ts-ignore
            label: "显示名称",
        }));
        const __VLS_334 = __VLS_333({
            label: "显示名称",
        }, ...__VLS_functionalComponentArgsRest(__VLS_333));
        const { default: __VLS_337 } = __VLS_nonNull(__VLS_335.slots);
        let __VLS_338;
        /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
        elInput;
        // @ts-ignore
        const __VLS_339 = __VLS_asFunctionalComponent1(__VLS_338, new __VLS_338({
            // @ts-ignore
            modelValue: (__VLS_unwrap(newName, {})),
        }));
        const __VLS_340 = __VLS_339({
            modelValue: (__VLS_unwrap(newName, {})),
        }, ...__VLS_functionalComponentArgsRest(__VLS_339));
        // @ts-ignore
        [newName,];
        var __VLS_335;
        let __VLS_343;
        /** @ts-ignore @type { | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button'] | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button']} */
        elButton;
        // @ts-ignore
        const __VLS_344 = __VLS_asFunctionalComponent1(__VLS_343, new __VLS_343({
            // @ts-ignore
            ...{ 'onClick': {} },
        }));
        const __VLS_345 = __VLS_344({
            ...{ 'onClick': {} },
        }, ...__VLS_functionalComponentArgsRest(__VLS_344));
        let __VLS_348;
        const __VLS_349 = {
            /** @type {typeof __VLS_348.click} */
            onClick: (saveProfile),
        };
        void __VLS_349;
        const { default: __VLS_350 } = __VLS_nonNull(__VLS_346.slots);
        // @ts-ignore
        [];
        var __VLS_346;
        var __VLS_347;
        let __VLS_351;
        /** @ts-ignore @type { | typeof __VLS_components.elDivider | typeof __VLS_components.ElDivider | typeof __VLS_components['el-divider']} */
        elDivider;
        // @ts-ignore
        const __VLS_352 = __VLS_asFunctionalComponent1(__VLS_351, new __VLS_351({
        // @ts-ignore
        }));
        const __VLS_353 = __VLS_352({}, ...__VLS_functionalComponentArgsRest(__VLS_352));
        let __VLS_356;
        /** @ts-ignore @type { | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item'] | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item']} */
        elFormItem;
        // @ts-ignore
        const __VLS_357 = __VLS_asFunctionalComponent1(__VLS_356, new __VLS_356({
            // @ts-ignore
            label: "当前密码",
        }));
        const __VLS_358 = __VLS_357({
            label: "当前密码",
        }, ...__VLS_functionalComponentArgsRest(__VLS_357));
        const { default: __VLS_361 } = __VLS_nonNull(__VLS_359.slots);
        let __VLS_362;
        /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
        elInput;
        // @ts-ignore
        const __VLS_363 = __VLS_asFunctionalComponent1(__VLS_362, new __VLS_362({
            // @ts-ignore
            modelValue: (__VLS_unwrap(oldPassword, {})), type: "password",
        }));
        const __VLS_364 = __VLS_363({
            modelValue: (__VLS_unwrap(oldPassword, {})),
            type: "password",
        }, ...__VLS_functionalComponentArgsRest(__VLS_363));
        // @ts-ignore
        [oldPassword,];
        var __VLS_359;
        let __VLS_367;
        /** @ts-ignore @type { | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item'] | typeof __VLS_components.elFormItem | typeof __VLS_components.ElFormItem | typeof __VLS_components['el-form-item']} */
        elFormItem;
        // @ts-ignore
        const __VLS_368 = __VLS_asFunctionalComponent1(__VLS_367, new __VLS_367({
            // @ts-ignore
            label: "新密码",
        }));
        const __VLS_369 = __VLS_368({
            label: "新密码",
        }, ...__VLS_functionalComponentArgsRest(__VLS_368));
        const { default: __VLS_372 } = __VLS_nonNull(__VLS_370.slots);
        let __VLS_373;
        /** @ts-ignore @type { | typeof __VLS_components.elInput | typeof __VLS_components.ElInput | typeof __VLS_components['el-input']} */
        elInput;
        // @ts-ignore
        const __VLS_374 = __VLS_asFunctionalComponent1(__VLS_373, new __VLS_373({
            // @ts-ignore
            modelValue: (__VLS_unwrap(password2, {})), type: "password",
        }));
        const __VLS_375 = __VLS_374({
            modelValue: (__VLS_unwrap(password2, {})),
            type: "password",
        }, ...__VLS_functionalComponentArgsRest(__VLS_374));
        // @ts-ignore
        [password2,];
        var __VLS_370;
        let __VLS_378;
        /** @ts-ignore @type { | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button'] | typeof __VLS_components.elButton | typeof __VLS_components.ElButton | typeof __VLS_components['el-button']} */
        elButton;
        // @ts-ignore
        const __VLS_379 = __VLS_asFunctionalComponent1(__VLS_378, new __VLS_378({
            // @ts-ignore
            ...{ 'onClick': {} }, type: "danger",
        }));
        const __VLS_380 = __VLS_379({
            ...{ 'onClick': {} },
            type: "danger",
        }, ...__VLS_functionalComponentArgsRest(__VLS_379));
        let __VLS_383;
        const __VLS_384 = {
            /** @type {typeof __VLS_383.click} */
            onClick: (changePassword),
        };
        void __VLS_384;
        const { default: __VLS_385 } = __VLS_nonNull(__VLS_381.slots);
        // @ts-ignore
        [];
        var __VLS_381;
        var __VLS_382;
        // @ts-ignore
        [];
        var __VLS_318;
    }
    // @ts-ignore
    [];
    var __VLS_105;
    // @ts-ignore
    [];
    var __VLS_93;
    // @ts-ignore
    [];
    var __VLS_23;
}
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
