import { ref } from 'vue';
const token = ref(localStorage.getItem('user_token') || '');
const email = ref('');
const password = ref('');
const display = ref('');
const mode = ref('login');
const profile = ref(null);
const error = ref('');
const newDisplay = ref('');
async function submit() { error.value = ''; const path = mode.value === 'login' ? '/api/v1/auth/login' : '/api/v1/auth/register'; const body = mode.value === 'login' ? { username: email.value, password: password.value } : { email: email.value, password: password.value, display_name: display.value }; const r = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }); const j = await r.json(); if (!r.ok) {
    error.value = j.detail || '请求失败';
    return;
} ; token.value = j.data.access_token; localStorage.setItem('user_token', token.value); await load(); }
async function load() { const r = await fetch('/api/v1/auth/me', { headers: { Authorization: `Bearer ${token.value}` } }); if (r.ok) {
    profile.value = (await r.json()).data;
    newDisplay.value = profile.value.display_name;
}
else
    logout(); }
async function saveProfile() { const r = await fetch('/api/v1/users/me', { method: 'PATCH', headers: { Authorization: `Bearer ${token.value}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ display_name: newDisplay.value }) }); if (r.ok)
    profile.value = (await r.json()).data;
else
    error.value = '保存失败'; }
function logout() { localStorage.removeItem('user_token'); token.value = ''; profile.value = null; }
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
__VLS_withDotValue(mode, {});
// @ts-ignore
__VLS_withDotValue(profile, {});
__VLS_asFunctionalElement1(__VLS_intrinsics.main, __VLS_intrinsics.main)({});
__VLS_asFunctionalElement1(__VLS_intrinsics.h1, __VLS_intrinsics.h1)({});
if (!token.value) {
    __VLS_asFunctionalElement1(__VLS_intrinsics.section, __VLS_intrinsics.section)({});
    let __VLS_0;
    /** @ts-ignore @type { | typeof __VLS_components.vanTabs | typeof __VLS_components.VanTabs | typeof __VLS_components['van-tabs'] | typeof __VLS_components.vanTabs | typeof __VLS_components.VanTabs | typeof __VLS_components['van-tabs']} */
    vanTabs;
    // @ts-ignore
    const __VLS_1 = __VLS_asFunctionalComponent1(__VLS_0, new __VLS_0({
        // @ts-ignore
        active: (mode.value),
    }));
    const __VLS_2 = __VLS_1({
        active: (mode.value),
    }, ...__VLS_functionalComponentArgsRest(__VLS_1));
    const { default: __VLS_5 } = __VLS_nonNull(__VLS_3.slots);
    let __VLS_6;
    /** @ts-ignore @type { | typeof __VLS_components.vanTab | typeof __VLS_components.VanTab | typeof __VLS_components['van-tab']} */
    vanTab;
    // @ts-ignore
    const __VLS_7 = __VLS_asFunctionalComponent1(__VLS_6, new __VLS_6({
        // @ts-ignore
        title: "登录", name: "login",
    }));
    const __VLS_8 = __VLS_7({
        title: "登录",
        name: "login",
    }, ...__VLS_functionalComponentArgsRest(__VLS_7));
    let __VLS_11;
    /** @ts-ignore @type { | typeof __VLS_components.vanTab | typeof __VLS_components.VanTab | typeof __VLS_components['van-tab']} */
    vanTab;
    // @ts-ignore
    const __VLS_12 = __VLS_asFunctionalComponent1(__VLS_11, new __VLS_11({
        // @ts-ignore
        title: "注册", name: "register",
    }));
    const __VLS_13 = __VLS_12({
        title: "注册",
        name: "register",
    }, ...__VLS_functionalComponentArgsRest(__VLS_12));
    // @ts-ignore
    [token, mode,];
    var __VLS_3;
    if (mode.value === 'register') {
        let __VLS_16;
        /** @ts-ignore @type { | typeof __VLS_components.vanField | typeof __VLS_components.VanField | typeof __VLS_components['van-field']} */
        vanField;
        // @ts-ignore
        const __VLS_17 = __VLS_asFunctionalComponent1(__VLS_16, new __VLS_16({
            // @ts-ignore
            modelValue: (__VLS_unwrap(display, {})), label: "昵称",
        }));
        const __VLS_18 = __VLS_17({
            modelValue: (__VLS_unwrap(display, {})),
            label: "昵称",
        }, ...__VLS_functionalComponentArgsRest(__VLS_17));
    }
    let __VLS_21;
    /** @ts-ignore @type { | typeof __VLS_components.vanField | typeof __VLS_components.VanField | typeof __VLS_components['van-field']} */
    vanField;
    // @ts-ignore
    const __VLS_22 = __VLS_asFunctionalComponent1(__VLS_21, new __VLS_21({
        // @ts-ignore
        modelValue: (__VLS_unwrap(email, {})), label: "邮箱",
    }));
    const __VLS_23 = __VLS_22({
        modelValue: (__VLS_unwrap(email, {})),
        label: "邮箱",
    }, ...__VLS_functionalComponentArgsRest(__VLS_22));
    let __VLS_26;
    /** @ts-ignore @type { | typeof __VLS_components.vanField | typeof __VLS_components.VanField | typeof __VLS_components['van-field']} */
    vanField;
    // @ts-ignore
    const __VLS_27 = __VLS_asFunctionalComponent1(__VLS_26, new __VLS_26({
        // @ts-ignore
        modelValue: (__VLS_unwrap(password, {})), type: "password", label: "密码",
    }));
    const __VLS_28 = __VLS_27({
        modelValue: (__VLS_unwrap(password, {})),
        type: "password",
        label: "密码",
    }, ...__VLS_functionalComponentArgsRest(__VLS_27));
    let __VLS_31;
    /** @ts-ignore @type { | typeof __VLS_components.vanButton | typeof __VLS_components.VanButton | typeof __VLS_components['van-button'] | typeof __VLS_components.vanButton | typeof __VLS_components.VanButton | typeof __VLS_components['van-button']} */
    vanButton;
    // @ts-ignore
    const __VLS_32 = __VLS_asFunctionalComponent1(__VLS_31, new __VLS_31({
        // @ts-ignore
        ...{ 'onClick': {} }, type: "primary", block: true,
    }));
    const __VLS_33 = __VLS_32({
        ...{ 'onClick': {} },
        type: "primary",
        block: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_32));
    let __VLS_36;
    const __VLS_37 = {
        /** @type {typeof __VLS_36.click} */
        onClick: (submit),
    };
    void __VLS_37;
    const { default: __VLS_38 } = __VLS_nonNull(__VLS_34.slots);
    (mode.value === 'login' ? '登录' : '注册');
    // @ts-ignore
    [mode, mode, display, email, password,];
    var __VLS_34;
    var __VLS_35;
    __VLS_asFunctionalElement1(__VLS_intrinsics.p, __VLS_intrinsics.p)({});
    (__VLS_unwrap(error, {}));
}
else {
    __VLS_asFunctionalElement1(__VLS_intrinsics.section, __VLS_intrinsics.section)({});
    let __VLS_39;
    /** @ts-ignore @type { | typeof __VLS_components.vanCell | typeof __VLS_components.VanCell | typeof __VLS_components['van-cell']} */
    vanCell;
    // @ts-ignore
    const __VLS_40 = __VLS_asFunctionalComponent1(__VLS_39, new __VLS_39({
        // @ts-ignore
        title: "账户 ID", value: (profile.value?.id),
    }));
    const __VLS_41 = __VLS_40({
        title: "账户 ID",
        value: (profile.value?.id),
    }, ...__VLS_functionalComponentArgsRest(__VLS_40));
    let __VLS_44;
    /** @ts-ignore @type { | typeof __VLS_components.vanCell | typeof __VLS_components.VanCell | typeof __VLS_components['van-cell']} */
    vanCell;
    // @ts-ignore
    const __VLS_45 = __VLS_asFunctionalComponent1(__VLS_44, new __VLS_44({
        // @ts-ignore
        title: "邮箱", value: (profile.value?.email),
    }));
    const __VLS_46 = __VLS_45({
        title: "邮箱",
        value: (profile.value?.email),
    }, ...__VLS_functionalComponentArgsRest(__VLS_45));
    let __VLS_49;
    /** @ts-ignore @type { | typeof __VLS_components.vanField | typeof __VLS_components.VanField | typeof __VLS_components['van-field']} */
    vanField;
    // @ts-ignore
    const __VLS_50 = __VLS_asFunctionalComponent1(__VLS_49, new __VLS_49({
        // @ts-ignore
        modelValue: (__VLS_unwrap(newDisplay, {})), label: "昵称",
    }));
    const __VLS_51 = __VLS_50({
        modelValue: (__VLS_unwrap(newDisplay, {})),
        label: "昵称",
    }, ...__VLS_functionalComponentArgsRest(__VLS_50));
    let __VLS_54;
    /** @ts-ignore @type { | typeof __VLS_components.vanButton | typeof __VLS_components.VanButton | typeof __VLS_components['van-button'] | typeof __VLS_components.vanButton | typeof __VLS_components.VanButton | typeof __VLS_components['van-button']} */
    vanButton;
    // @ts-ignore
    const __VLS_55 = __VLS_asFunctionalComponent1(__VLS_54, new __VLS_54({
        // @ts-ignore
        ...{ 'onClick': {} }, block: true, type: "primary",
    }));
    const __VLS_56 = __VLS_55({
        ...{ 'onClick': {} },
        block: true,
        type: "primary",
    }, ...__VLS_functionalComponentArgsRest(__VLS_55));
    let __VLS_59;
    const __VLS_60 = {
        /** @type {typeof __VLS_59.click} */
        onClick: (saveProfile),
    };
    void __VLS_60;
    const { default: __VLS_61 } = __VLS_nonNull(__VLS_57.slots);
    // @ts-ignore
    [error, profile, profile, newDisplay,];
    var __VLS_57;
    var __VLS_58;
    let __VLS_62;
    /** @ts-ignore @type { | typeof __VLS_components.vanButton | typeof __VLS_components.VanButton | typeof __VLS_components['van-button'] | typeof __VLS_components.vanButton | typeof __VLS_components.VanButton | typeof __VLS_components['van-button']} */
    vanButton;
    // @ts-ignore
    const __VLS_63 = __VLS_asFunctionalComponent1(__VLS_62, new __VLS_62({
        // @ts-ignore
        ...{ 'onClick': {} }, block: true,
    }));
    const __VLS_64 = __VLS_63({
        ...{ 'onClick': {} },
        block: true,
    }, ...__VLS_functionalComponentArgsRest(__VLS_63));
    let __VLS_67;
    const __VLS_68 = {
        /** @type {typeof __VLS_67.click} */
        onClick: (logout),
    };
    void __VLS_68;
    const { default: __VLS_69 } = __VLS_nonNull(__VLS_65.slots);
    // @ts-ignore
    [];
    var __VLS_65;
    var __VLS_66;
}
// @ts-ignore
[];
const __VLS_export = (await import('vue')).defineComponent({});
export default {};
