from pathlib import Path
p = Path("frameflow-app/pom.xml")
t = p.read_text()
old = """            <artifactId>frameflow-module-identity</artifactId>
        </dependency>
        <dependency>
            <groupId>com.frameflow</groupId>
            <artifactId>frameflow-module-product</artifactId>"""
new = """            <artifactId>frameflow-module-identity</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.frameflow</groupId>
            <artifactId>frameflow-module-product</artifactId>"""
assert old in t, "pattern missing"
t = t.replace(old, new, 1)
p.write_text(t)
print("patched")
