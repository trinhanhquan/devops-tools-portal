import sys
from lxml import etree
import html
import re

# Các thuộc tính Jira muốn bỏ qua để không gây noise
IGNORE_ATTRS = {
    "id",
    "sequence",
    "timestamp",
    "lastModified",
    "updated",
    "created",
    "version"
}

# Bật / tắt việc ignore thay đổi liên quan UUID
IGNORE_UUID_CHANGES = True

# UUID / GUID dạng phổ biến:
# - 8-4-4-4-12 hex
# - hoặc nhóm hex dài với dấu '-' giữa (như ví dụ bạn đưa)
UUID_LIKE_REGEX = re.compile(
    r"[0-9a-fA-F]{8,}-[0-9a-fA-F]{4,}-[0-9a-fA-F]{4,}-[0-9a-fA-F]{4,}"
)

def normalize_uuid_like(value: str) -> str:
    """
    Thay mọi chuỗi giống UUID / GUID trong string bằng <UUID>.
    Dùng cho cả attr value và text.
    """
    if value is None:
        return ""
    return UUID_LIKE_REGEX.sub("<UUID>", value.strip())


def clean_element(elem):
    """Xoá thuộc tính không cần và normalize text."""
    for attr in list(elem.attrib.keys()):
        if attr in IGNORE_ATTRS:
            del elem.attrib[attr]

    if elem.text:
        elem.text = elem.text.strip() or None

    if elem.tail:
        elem.tail = elem.tail.strip() or None

    for child in elem:
        clean_element(child)


def build_child_path(parent_path, child, position):
    """Tạo XPath đẹp và ngắn gọn."""
    if "key" in child.attrib:
        return f"{parent_path}/{child.tag}[@key='{child.attrib['key']}']"
    if "name" in child.attrib:
        return f"{parent_path}/{child.tag}[@name='{child.attrib['name']}']"
    return f"{parent_path}/{child.tag}[{position}]"


def diff_elements(e1, e2, path, changes):
    """So sánh toàn bộ node."""
    if e1.tag != e2.tag:
        changes.append(("REPLACED_NODE", path, e1.tag, e2.tag))
        return

    # Compare attributes (sau này sẽ so bằng bản đã normalize UUID)
    attrs1 = dict(e1.attrib)
    attrs2 = dict(e2.attrib)

    for a in attrs1.keys() - attrs2.keys():
        changes.append(("REMOVED_ATTR", f"{path}/@{a}", attrs1[a], ""))

    for a in attrs2.keys() - attrs1.keys():
        changes.append(("ADDED_ATTR", f"{path}/@{a}", "", attrs2[a]))

    for a in attrs1.keys() & attrs2.keys():
        v1 = attrs1[a]
        v2 = attrs2[a]

        if IGNORE_UUID_CHANGES:
            n1 = normalize_uuid_like(v1)
            n2 = normalize_uuid_like(v2)
            if n1 == n2:
                # Chỉ khác phần UUID → bỏ qua
                continue
        if v1 != v2:
            changes.append(("CHANGED_ATTR", f"{path}/@{a}", v1, v2))

    # Compare text (cũng dùng normalize UUID)
    t1 = (e1.text or "")
    t2 = (e2.text or "")

    if IGNORE_UUID_CHANGES:
        n1 = normalize_uuid_like(t1)
        n2 = normalize_uuid_like(t2)
        if n1 == n2:
            # Chỉ khác UUID embedded → bỏ qua
            pass
        else:
            if t1.strip() or t2.strip():
                changes.append(("CHANGED_TEXT", f"{path}/text()", t1, t2))
    else:
        if t1 != t2 and (t1.strip() or t2.strip()):
            changes.append(("CHANGED_TEXT", f"{path}/text()", t1, t2))

    # Compare children
    children1 = list(e1)
    children2 = list(e2)
    max_len = max(len(children1), len(children2))

    for i in range(max_len):
        pos = i + 1
        if i >= len(children1):
            c2 = children2[i]
            child_path = build_child_path(path, c2, pos)
            changes.append(
                ("ADDED_NODE", child_path, "", etree.tostring(c2, encoding="unicode"))
            )
        elif i >= len(children2):
            c1 = children1[i]
            child_path = build_child_path(path, c1, pos)
            changes.append(
                ("REMOVED_NODE", child_path, etree.tostring(c1, encoding="unicode"), "")
            )
        else:
            c1 = children1[i]
            c2 = children2[i]
            child_path = build_child_path(path, c1, pos)
            diff_elements(c1, c2, child_path, changes)


def compare_xml(before_file, after_file):
    parser = etree.XMLParser(remove_blank_text=True)
    root1 = etree.parse(before_file, parser).getroot()
    root2 = etree.parse(after_file, parser).getroot()

    print(f"Root before : /{root1.tag}")
    print(f"Root after  : /{root2.tag}")

    clean_element(root1)
    clean_element(root2)

    changes = []
    diff_elements(root1, root2, f"/{root1.tag}", changes)
    return changes


def save_report(changes, output_file):
    """Xuất report ra file (txt hoặc html)."""
    is_html = output_file.lower().endswith(".html")

    with open(output_file, "w", encoding="utf-8") as f:

        header = f"Found {len(changes)} changes"
        if IGNORE_UUID_CHANGES:
            header += " (changes that differ only by UUID-like values were ignored)."
        else:
            header += "."

        if is_html:
            f.write("<html><body><h2>XML Difference Report</h2>")
            f.write(f"<p>{html.escape(header)}</p>")
            f.write("<pre style='font-size:14px'>")
        else:
            f.write(header + "\n\n")

        if not changes:
            msg = "No differences after applying filters."
            f.write(html.escape(msg) if is_html else msg)
        else:
            for change_type, xpath, old, new in changes:
                if is_html:
                    f.write(f"<b>{change_type}</b>: "
                            f"<span style='color:blue'>{html.escape(xpath)}</span>\n")
                    if old:
                        f.write(f"<span style='color:red'>  - old:</span> "
                                f"{html.escape(old)}\n")
                    if new:
                        f.write(f"<span style='color:green'>  - new:</span> "
                                f"{html.escape(new)}\n")
                    f.write("\n")
                else:
                    f.write(f"{change_type}: {xpath}\n")
                    if old:
                        f.write(f"  - old: {old}\n")
                    if new:
                        f.write(f"  - new: {new}\n")
                    f.write("\n")

        if is_html:
            f.write("</pre></body></html>")

    print(f"✅ Report written to: {output_file}")
    print(f"   Total changes: {len(changes)}")


def main():
    if len(sys.argv) != 4:
        print("Usage: python compare_jira_xml_xpath.py before.xml after.xml report.txt|report.html")
        sys.exit(1)

    before_file = sys.argv[1]
    after_file = sys.argv[2]
    report_file = sys.argv[3]

    print(f"🔄 Comparing:\n  BEFORE: {before_file}\n  AFTER : {after_file}\n")

    changes = compare_xml(before_file, after_file)
    save_report(changes, report_file)


if __name__ == "__main__":
    main()
