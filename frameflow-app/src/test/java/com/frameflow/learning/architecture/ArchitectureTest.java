package com.frameflow.learning.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * T4 架构守护：模块边界不再靠口头约定，而是靠测试。
 * 规则被打破（比如有人图省事从 product 直接查 identity 的表）时，
 * 构建直接失败——债务在产生的那一刻就被看见。
 *
 * 写法说明：用普通 @Test + ClassFileImporter 直接检查字节码，
 * 不走 ArchUnit 专用 JUnit 引擎（那个引擎与 surefire 的筛选机制
 * 存在兼容坑，会出现"Tests run: 0"假绿——规则看似存在实则没跑）。
 */
class ArchitectureTest {

    /** 只导入生产代码（main），测试类自身不受业务规则约束。 */
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.frameflow.learning");

    // ★ 核心（docs/02 §3 模块边界）：product 跨模块只允许走 identity 的
    // 公开接口（service/domain），绝不允许直接依赖 identity 的数据层——
    // 否则 identity 改表结构会静默炸掉 product，模块化就名存实亡。
    @Test
    void product_不得触碰_identity数据层() {
        ArchRule rule = noClasses().that().resideInAPackage("..product..")
                .should().dependOnClassesThat().resideInAPackage("..identity.repo..");
        rule.check(classes);
    }

    // Controller 只允许通过 Service 干活：web 依赖 repo 意味着业务规则
    // 从 Service 泄漏到了接口层，权限与校验将变得不可测试。
    @Test
    void 接口层不得直连数据层() {
        ArchRule rule = noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAPackage("..repo..");
        rule.check(classes);
    }

    // shared 是被所有人依赖的公共底座，它自己不许反向依赖任何业务模块，
    // 否则会出现循环依赖。
    @Test
    void shared不得依赖业务模块() {
        ArchRule rule = noClasses().that().resideInAPackage("..shared..")
                .should().dependOnClassesThat().resideInAnyPackage("..identity..", "..product..");
        rule.check(classes);
    }
}
