import argparse

from spam_filter.classifier import NaiveBayesSpamClassifier
from spam_filter.email_parser import parse_eml_file


def build_argument_parser():
    parser = argparse.ArgumentParser(description="使用训练好的模型识别垃圾邮件")
    parser.add_argument("--model", default="models/spam_model.json", help="模型文件路径")
    parser.add_argument("--subject", default="", help="邮件主题")
    parser.add_argument("--body", default="", help="邮件正文")
    parser.add_argument("--eml", help="原始 .eml 邮件路径")
    return parser


def main():
    args = build_argument_parser().parse_args()
    model = NaiveBayesSpamClassifier.load(args.model)

    if args.eml:
        subject, body = parse_eml_file(args.eml)
    else:
        subject, body = args.subject, args.body

    if not subject and not body:
        raise ValueError("请提供 --subject/--body，或者传入 --eml 文件。")

    predicted_label, probabilities = model.predict(subject, body)
    spam_probability = probabilities.get("spam", 0.0)
    ham_probability = probabilities.get("ham", 0.0)

    print("邮件主题: %s" % (subject or "<空主题>"))
    print("识别结果: %s" % ("垃圾邮件" if predicted_label == "spam" else "正常邮件"))
    print("spam 概率: {:.2%}".format(spam_probability))
    print("ham  概率: {:.2%}".format(ham_probability))


if __name__ == "__main__":
    main()
