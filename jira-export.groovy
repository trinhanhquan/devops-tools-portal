import sys
from lxml import etree

# Những thuộc tính Jira thường không muốn so sánh
IGNORE_ATTRS = {
    "id",
    "sequence",
    "timestamp",
    "lastModified",
    "updated",
    "created",
    "version"
}


def clean_element(elem):
    """
    Xoá các thuộc tính không quan trọng (IGNORE_ATTRS)
    và chuẩn hóa text (strip).
    """
    for attr in list(elem.attrib.keys()):
        if attr in IGNORE_ATTRS:
            del elem.attrib[attr]

    # Chuẩn hóa text
    if elem.text is not None:
        elem.text = elem.text.strip() or None

    if elem.tail is not None:
        elem.tail = elem.tail.strip() or None

    for child in elem:
        clean_element(child)


def build_child_path(parent_path, child, position):
    """
    Tạo XPath tương đối “đẹp”:
    - Nếu có @key thì dùng @key
    - Nếu có @name thì dùng @name
    - Nếu không có thì dùng index [position]
    """
    if "key" in child.attrib:
        return f"{parent_path}/{child.tag}[@key='{child.attrib['key']}']"
    if "name" in child.attrib:
        return f"{parent_path}/{child.tag}[@name='{child.attrib['name']}']"
    return f"{parent_path}/{child.tag}[{position}]"


def diff_elements(e1, e2, path, changes):
    """
    So sánh hai element cùng vị trí, ghi lại mọi khác biệt:
    - Thuộc tính thêm / xóa / đổi
    - Text khác
    - Node con thêm / xóa / khác
    """
    # Nếu tag khác => coi như node bị replace
    if e1.tag != e2.tag:
        changes.append(("REPLACED_NODE", path, e1.tag, e2.tag))
        return

    # So sánh attributes (đã bỏ IGNORE_ATTRS trước đó)
    attrs1 = dict(e1.attrib)
    attrs2 = dict(e2.attrib)

    # Thuộc tính bị xóa
    for a in attrs1.keys() - attrs2.keys():
        changes.append(("REMOVED_ATTR", f"{path}/@{a}", attrs1[a], ""))

    # Thuộc tính mới
    for a in attrs2.keys() - attrs1.keys():
        changes.append(("ADDED_ATTR", f"{path}/@{a}", "", attrs2[a]))

    # Thuộc tính đổi giá trị
    for a in attrs1.keys() & attrs2.keys():
        if attrs1[a] != attrs2[a]:
            changes.append(("CHANGED_ATTR", f"{path}/@{a}", attrs1[a], attrs2[a]))

    # So sánh text
    t1 = (e1.text or "").strip()
    t2 = (e2.text or "").strip()
    if t1 != t2:
        # Nếu cả 2 đều rỗng thì bỏ qua
        if t1 or t2:
            changes.append(("CHANGED_TEXT", f"{path}/text()", t1, t2))

    # So sánh children theo index (giả định Jira export giữ thứ tự ổn định)
    children1 = list(e1)
    children2 = list(e2)
    max_len = max(len(children1), len(children2))

    for i in range(max_len):
        pos = i + 1
        if i >= len(children1):
            # Node mới trong after
            c2 = children2[i]
            child_path = build_child_path(path, c2, pos)
            changes.append(
                ("ADDED_NODE", child_path, "", etree.tostring(c2, encoding="unicode"))
            )
        elif i >= len(children2):
            # Node bị xoá trong after
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

    tree1 = etree.parse(before_file, parser)
    tree2 = etree.parse(after_file, parser)

    root1 = tree1.getroot()
    root2 = tree2.getroot()

    clean_element(root1)
    clean_element(root2)

    changes = []
    root_path = f"/{root1.tag}"

    diff_elements(root1, root2, root_path, changes)

    return changes


def print_report(changes):
    if not changes:
        print("✅ No differences found (after ignoring noise attributes).")
        return

    print(f"⚠ Found {len(changes)} changes:\n")

    for change_type, xpath, old, new in changes:
        print(f"{change_type}: {xpath}")
        if change_type in ("CHANGED_ATTR", "CHANGED_TEXT"):
            print(f"  - old: {old}")
            print(f"  - new: {new}")
        elif change_type in ("ADDED_NODE", "REMOVED_NODE", "REPLACED_NODE"):
            if old:
                print(f"  - old: {old}")
            if new:
                print(f"  - new: {new}")
        print("")


def main():
    if len(sys.argv) != 3:
        print("Usage: python compare_jira_xml_xpath.py before.xml after.xml")
        sys.exit(1)

    before_file = sys.argv[1]
    after_file = sys.argv[2]

    print(f"🔄 Comparing:\n  BEFORE: {before_file}\n  AFTER : {after_file}\n")

    changes = compare_xml(before_file, after_file)
    print_report(changes)


if __name__ == "__main__":
    main()
