"""
Unit tests for permanent Excel workspace export and sheet completeness.
"""

import os
import tempfile
import pytest
import openpyxl
from src.database import get_db, init_db
from src.excel import export_master_catalog

def test_export_master_catalog_has_all_13_sheets():
    with tempfile.TemporaryDirectory() as tmpdir:
        output_path = os.path.join(tmpdir, "test_master_catalog.xlsx")
        
        # Ensure db is initialized and export
        init_db()
        with get_db() as db:
            wb = export_master_catalog(db, output_path=output_path)
            
        assert os.path.exists(output_path)
        
        # Load workbook and verify sheets
        loaded_wb = openpyxl.load_workbook(output_path)
        sheet_names = loaded_wb.sheetnames
        
        expected_sheets = [
            "README",
            "Suppliers",
            "Products",
            "Product Plans",
            "Price Observations",
            "Cheapest Verified",
            "Price History",
            "Retail Benchmarks",
            "FX Rates",
            "Research Queue",
            "Risk Review",
            "Implementation Tasks",
            "Audit Log"
        ]
        
        assert len(sheet_names) == 13
        for sheet in expected_sheets:
            assert sheet in sheet_names, f"Missing required worksheet: {sheet}"
            
        # Verify README sheet has title content
        ws_readme = loaded_wb["README"]
        assert "AIToolGems" in str(ws_readme["A2"].value)
