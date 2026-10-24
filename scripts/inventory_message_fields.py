"""Read every available research SMS locally; emit field-presence counts only.
These are regex evidence signals, not validated extracted financial facts.
"""
import collections
import hashlib
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
RULES = {
    "money_amount": r"(?:₹|\brs\.?|\binr\.?)\s*-?\s*\d[\d,]*(?:\.\d+)?",
    "account_identifier": r"\b(?:a\s*/\s*c|acct|account|acc|ac)\b.{0,30}(?:\d|[xX*]{2})",
    "card_identifier": r"\bcard\b.{0,35}(?:ending|\d|[xX*]{2})",
    "upi_address": r"\b[a-z0-9][a-z0-9._-]*@[a-z][a-z0-9]+\b",
    "transaction_reference": r"\b(?:rrn|utr|refno|upi\s*ref|txn\s*(?:id|no)?|transaction\s*(?:id|no)|reference\s*(?:no|number)?)\b",
    "balance_wording": r"\b(?:bal(?:ance)?|avl\.?\s*bal|available\s+balance|new\s+bal)\b",
    "negative_balance_candidate": r"\b(?:bal(?:ance)?|avl\.?\s*bal|new\s+bal)\s*[:.]?\s*(?:(?:₹|rs\.?|inr\.?)\s*)?-\s*\d",
    "available_balance_candidate": r"\b(?:avl|available|avail)\.?\s*bal(?:ance)?\b",
    "credit_limit": r"\b(?:available|avl|total|credit|remaining)\s+(?:credit\s+)?limit\b",
    "total_due": r"\b(?:total\s+(?:amount\s+)?due|total\s+outstanding|outstanding\s+(?:amount|balance)|amount\s+due)\b",
    "minimum_due": r"\b(?:min(?:imum)?\.?\s+(?:amount\s+)?due|mad)\b",
    "bill_or_statement": r"\b(?:bill|statement|billing)\b",
    "due_or_expiry_date": r"\b(?:due\s+(?:on|date|by)|pay\s+by|valid\s+(?:till|until)|expires?\s+(?:on|in)|expiry|ending\s+on)\b",
    "emi_or_loan": r"\b(?:emi|loan|instalments?|installments?)\b",
    "loan_identifier": r"\b(?:loan\s*(?:a\s*/\s*c|account|no|number|id)|lan\s*[:#])",
    "overdue_or_arrears": r"\b(?:overdue|arrears|past\s+due|days\s+past\s+due|dpd|npa)\b",
    "fee_penalty_or_interest": r"\b(?:fees?|charges?|penal(?:ty)?|interest|surcharge)\b",
    "mandate_or_autopay": r"\b(?:mandate|autopay|auto-pay|automatic\s+payment|nach|ecs|sihubid)\b",
    "mandate_ceiling_candidate": r"\b(?:mandate|autopay)\b.{0,180}\b(?:up\s*to|maximum|max|limit)\b|\b(?:up\s*to|maximum)\b.{0,90}\bmandate\b",
    "scheduled_or_future_movement": r"\b(?:scheduled|will\s+be\s+(?:debited|credited|presented)|to\s+be\s+(?:debited|presented))\b",
    "mandate_cancellation_or_pause": r"\b(?:mandate|autopay)\b.{0,100}\b(?:cancelled|canceled|revoked|paused|pause|cancel)\b|\b(?:cancelled|revoked|paused)\b.{0,60}\bmandate\b",
    "failed_declined_or_negated": r"\b(?:failed|declined|unsuccessful|not\s+debited|could\s+not\s+be\s+processed|rejection|insufficient|bounced)\b",
    "refund_or_reversal": r"\b(?:refund(?:ed)?|reversal|reversed)\b",
    "pending_refund_candidate": r"\brefund\b.{0,100}\b(?:initiated|within|expected|by)\b|\bwill\s+be\s+refunded\b",
    "cashback_reward_or_voucher": r"\b(?:cashback|reward|voucher|coupon)\b",
    "receipt_or_acknowledgement": r"\b(?:receipt|acknowledg\w*|payment\s+of.{0,80}(?:received|successful)|recharge.{0,80}successful|thank\s+you\s+for\s+your\s+purchase)\b",
    "order_identifier": r"\b(?:order\s*(?:id|no|number)|booking\s*(?:id|no)|pnr)\b",
    "toll_or_fastag": r"\b(?:fastag|toll|plaza)\b",
    "atm_or_cash_withdrawal": r"\b(?:atm|cash\s+withdrawal|withdrawn)\b",
    "credit_ledger_or_udhaar": r"\b(?:khatabook|udhaar)\b",
    "telecom_plan_or_data": r"\b(?:recharge|talktime|data\s+balance|validity|gb|mb|prepaid|postpaid)\b",
    "suspension_or_disconnection": r"\b(?:suspended|disconnected|disconnection|services\s+will\s+stop|non-payment)\b",
    "otp_or_login_code": r"\b(?:otp|one.time\s+password|verification\s+code|login\s+code)\b",
    "security_or_device_linking": r"\b(?:new\s+device|registration|registered|linked|login|password|pin\s+changed)\b",
    "dispute_or_support": r"\b(?:dispute|unauthori[sz]ed|not\s+done\s+by\s+you|if\s+not|helpline|report\s+fraud)\b",
    "url": r"https?://|www\.",
    "clock_time": r"(?<![\d.])(?:[01]?\d|2[0-3]):[0-5]\d(?::[0-5]\d)?(?!\d)",
    "date_like": r"\b\d{1,2}(?:[- /]?)(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*(?:[- /]?)\d{2,4}\b|\b\d{1,4}[-/.]\d{1,2}[-/.]\d{2,4}\b",
    "telugu_script": r"[\u0c00-\u0c7f]",
    "devanagari_script": r"[\u0900-\u097f]",
}
PATTERNS = {key: re.compile(rx, re.I | re.S) for key, rx in RULES.items()}

def main():
    files = [ROOT / "clauds work/raw/sms.json"] + sorted((ROOT / "clauds work").glob("device*/raw/sms.json"))
    counts = collections.Counter()
    unique = set()
    per_device = []
    for file in files:
        rows = json.loads(file.read_text(encoding="utf-8"))
        stats = collections.Counter()
        for row in rows:
            stats["all_rows_scanned"] += 1
            text = row.get("body", "") or ""
            # Each row is inspected; only received rows contribute to field inventory.
            hits = [name for name, pattern in PATTERNS.items() if pattern.search(text)]
            if row.get("type") != 1:
                continue
            stats["received_rows"] += 1
            digest = hashlib.sha256((str(row.get("address", "")) + "|" + str(row.get("date", "")) + "|" + text).encode()).digest()
            if digest in unique:
                continue
            unique.add(digest)
            counts["unique_received_rows"] += 1
            if not hits:
                counts["no_inventory_signal"] += 1
            counts.update(hits)
        per_device.append({"dataset": file.parent.parent.name, **stats})
    report = {"scope": "Every exported SMS row inspected locally; counts below are exact-deduplicated received-message field-presence signals, not validated financial facts.", "datasets": per_device, "inventory": dict(counts)}
    destination = ROOT / "artifacts/message-field-inventory.json"
    destination.parent.mkdir(exist_ok=True)
    destination.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))

if __name__ == "__main__":
    main()
