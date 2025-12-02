import sys
from lxml import etree
import html
import re

# Ignore Jira-generated attributes
IGNORE_ATTRS = {
    "id", "sequence", "timestamp", "lastModified",
    "updated", "created", "version"
}

# Ignore UUID-like differences
IGNORE_UUID_CHANGES = True

# Detect any UUID-like pattern
UUID_LIKE_REGEX = re.compile(
    r"[0-9a-fA-F]{8,}-[0-9a-fA-F]{4,}-[0-9a-fA-F]{4,}-[0-9a-fA-F]{4,}"
)

def normalize_uuid_like(value: str) -> str:
    """Replace UUID-like segments with <UUID>."""
    if value is None:
        return ""
    return UUID_LIKE_REGEX.sub("<UUID>", value.strip())


def clean_element(elem):
    """Remove Jira noise attrs & normalize text."""
    for attr in list(elem.attrib.keys()):
        if attr in IGNORE_ATTRS:
            del elem.attrib[attr]

    if elem.text:
        elem.text = elem.text.strip() or None
    if elem.tail:
        elem.tail = elem.tail.strip() or None

    for child in elem:
        clean_element(child)


def highlight_diff(old, new):
    """Highlight changed substring (yellow/red) and unchanged parts (green)."""

    o = old or ""
    n = new or ""

    # If identical → whole text green
    if o == n:
        green_text = f"<span style='color:green'>{html.escape(o)}</span>"
        return green_text, green_text

    # Find first mismatch
    start = 0
    min_len = min(len(o), len(n))
    while start < min_len and o[start] == n[start]:
        start += 1

    # Find last mismatch
    end_o = len(o) - 1
    end_n = len(n) - 1

    while end_o >= 0 and end_n >= 0 and o[end_o] == n[end_n] and end_o >= start and end_n >= start:
        end_o -= 1
        end_n -= 1

    # Split into 3 regions
    o_pre = html.escape(o[:start])
    o_mid = html.escape(o[start:end_o + 1])
    o_post = html.escape(o[end_o + 1:])

    n_pre = html.escape(n[:start])
    n_mid = html.escape(n[start:end_n + 1])
    n_post = html.escape(n[end_n + 1:])

    o_final = (
        f"<span style='color:green'>{o_pre}</span>"
        f"<span style='background:yellow;color:red;font-weight:bold'>{o_mid}</span>"
        f"<span style='color:green'>{o_post}</span>"
    )

    n_final = (
        f"<span style='color:green'>{n_pre}</span>"
        f"<span style='background:yellow;color:red;font-weight:bold'>{n_mid}</span>"
        f"<span style='color:green'>{n_post}</span>"
    )

    return o_final, n_final



def build_child_path(parent_path, child, position):
    """Build readable XPath using key/name if available."""
    if "key" in child.attrib:
        return f"{parent_path}/{child.tag}[@key='{child.attrib['key']}']"
    if "name" in child.attrib:
        return f"{parent_path}/{child.tag}[@name='{child.attrib['name']}']"
    return f"{parent_path}/{child.tag}[{position}]"


def diff_elements(e1, e2, path, changes):
    """Compare elements recursively."""
    if e1.tag != e2.tag:
        changes.append(("REPLACED_NODE", path, e1.tag, e2.tag))
        return

    attrs1 = dict(e1.attrib)
    attrs2 = dict(e2.attrib)

    # Removed attributes
    for a in attrs1.keys() - attrs2.keys():
        changes.append(("REMOVED_ATTR", f"{path}/@{a}", attrs1[a], ""))

    # Added attributes
    for a in attrs2.keys() - attrs1.keys():
        changes.append(("ADDED_ATTR", f"{path}/@{a}", "", attrs2[a]))

    # Modified attributes
    for a in attrs1.keys() & attrs2.keys():
        v1, v2 = attrs1[a], attrs2[a]

        if IGNORE_UUID_CHANGES:
            if normalize_uuid_like(v1) == normalize_uuid_like(v2):
                continue

        if v1 != v2:
            changes.append(("CHANGED_ATTR", f"{path}/@{a}", v1, v2))

    # Compare text content
    t1 = e1.text or ""
    t2 = e2.text or ""

    if IGNORE_UUID_CHANGES:
        if normalize_uuid_like(t1) != normalize_uuid_like(t2):
            if t1.strip() or t2.strip():
                changes.append(("CHANGED_TEXT", f"{path}/text()", t1, t2))
    else:
        if t1 != t2:
            changes.append(("CHANGED_TEXT", f"{path}/text()", t1, t2))

    # Compare children by index
    c1 = list(e1)
    c2 = list(e2)
    max_len = max(len(c1), len(c2))

    for i in range(max_len):
        pos = i + 1

        if i >= len(c1):
            changes.append(("ADDED_NODE", build_child_path(path, c2[i], pos), "", etree.tostring(c2[i], encoding="unicode")))
        elif i >= len(c2):
            changes.append(("REMOVED_NODE", build_child_path(path, c1[i], pos), etree.tostring(c1[i], encoding="unicode"), ""))
        else:
            diff_elements(c1[i], c2[i], build_child_path(path, c1[i], pos), changes)


def compare_xml(before_file, after_file):
    parser = etree.XMLParser(remove_blank_text=True)
    r1 = etree.parse(before_file, parser).getroot()
    r2 = etree.parse(after_file, parser).getroot()

    clean_element(r1)
    clean_element(r2)

    changes = []
    diff_elements(r1, r2, f"/{r1.tag}", changes)
    return changes


def save_report(changes, output_file):
    """Write HTML or TXT report with highlight colors."""
    is_html = output_file.endswith(".html")

    with open(output_file, "w", encoding="utf-8") as f:

        if is_html:
            f.write("<html><body><h2>XML Compare Report</h2><pre>")

        if not changes:
            msg = "No differences (UUID-only changes ignored)."
            f.write(html.escape(msg) if is_html else msg)
            return

        for change_type, xpath, old, new in changes:

            if change_type in ("CHANGED_ATTR", "CHANGED_TEXT"):
                old_h, new_h = highlight_diff(old, new)
            else:
                old_h, new_h = html.escape(str(old)), html.escape(str(new))

            if is_html:
                f.write(f"<b>{change_type}</b>: <span style='color:blue'>{html.escape(xpath)}</span>\n")
                if old:
                    f.write(f"  - old: {old_h}\n")
                if new:
                    f.write(f"  - new: {new_h}\n")
                f.write("\n")
            else:
                f.write(f"{change_type}: {xpath}\n")
                if old: f.write(f"  - old: {old}\n")
                if new: f.write(f"  - new: {new}\n")
                f.write("\n")

        if is_html:
            f.write("</pre></body></html>")

    print(f"✔ Report saved → {output_file}")


def main():
    if len(sys.argv) != 4:
        print("Usage: python compare_jira_xml_xpath.py before.xml after.xml report.html")
        return

    before, after, report = sys.argv[1:]
    changes = compare_xml(before, after)
    save_report(changes, report)


if __name__ == "__main__":
    main()
