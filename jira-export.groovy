import sys
from lxml import etree
from difflib import HtmlDiff

# Những thuộc tính cần bỏ qua (noise của Jira XML export)
IGNORE_ATTRS = {
    "id",
    "sequence",
    "timestamp",
    "lastModified",
    "updated",
    "created",
    "version"
}

def normalize_xml(input_file):
    parser = etree.XMLParser(remove_blank_text=True)
    tree = etree.parse(input_file, parser)
    root = tree.getroot()

    def clean(elem):
        # Xoá thuộc tính không quan trọng
        for attr in list(elem.attrib.keys()):
            if attr in IGNORE_ATTRS:
                del elem.attrib[attr]

        # Sắp xếp attributes cho stable diff
        elem.attrib.update(dict(sorted(elem.attrib.items())))

        # Normalize children
        for child in elem:
            clean(child)

    clean(root)

    # Pretty print để diff dễ nhìn
    xml_str = etree.tostring(
        root, pretty_print=True, encoding="unicode"
    )
    return xml_str.splitlines()


def generate_html_diff(before_lines, after_lines, output_html):
    diff = HtmlDiff().make_file(
        before_lines, after_lines,
        fromdesc="Before XML",
        todesc="After XML"
    )

    with open(output_html, "w", encoding="utf-8") as f:
        f.write(diff)

    print(f"✅ Compare report generated: {output_html}")


def main():
    if len(sys.argv) != 4:
        print("Usage: python compare_jira_xml.py before.xml after.xml report.html")
        sys.exit(1)

    before_file = sys.argv[1]
    after_file = sys.argv[2]
    output_file = sys.argv[3]

    print("🔄 Normalizing XML files...")
    before = normalize_xml(before_file)
    after = normalize_xml(after_file)

    print("🔍 Generating HTML diff report...")
    generate_html_diff(before, after, output_file)


if __name__ == "__main__":
    main()
