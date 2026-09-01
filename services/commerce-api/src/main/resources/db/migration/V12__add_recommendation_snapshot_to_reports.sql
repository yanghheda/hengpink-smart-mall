ALTER TABLE decision_reports
    ADD COLUMN recommendation_snapshot_json JSON NULL AFTER report_json;
