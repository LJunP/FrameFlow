from pathlib import Path
p = Path("frameflow-modules/product/pom.xml")
t = p.read_text()
needle = '        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>'
add = needle + '
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>provided</scope>
        </dependency>'
assert needle in t, "needle missing"
t = t.replace(needle, add, 1)
p.write_text(t)
print("pom patched")
