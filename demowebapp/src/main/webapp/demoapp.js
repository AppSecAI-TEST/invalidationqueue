

function loadAccounts() {
    $.ajax({
        url: "/accounts/v1/accounts"
    }).then(function(data) {
        $("#accounts").html("<table id='accountTable'></table>");
        var table = $("#accountTable");
        table.append("<tr><th>Account</th><th>Balance</th></tr>")
        $.each(data['accounts'], function(i, account) {
            table.append("<tr><td>" + account["accountNumber"] + "</td><td>" + account["balance"] + "</td></tr>");
        });
    });
}

function configureMoveMoneyForm() {
    $("#movemoneyForm").submit(function(event) {
        $.ajax({
            type: "POST",
            url: "/movemoney/v1/spend",
            data: $("#movemoneyForm").serialize(),
            success: function() {
                alert("Successfully moved money.");
            },
            error: function() {
                alert("Error moving money.");
            }
        });
        event.preventDefault();
    });
}

function configureAccountRefresh() {
    $("#accountRefresh").click(function(event) {
        $("#accounts").html("Loading...");
        loadAccounts();
    });
}



$(document).ready(function() {
    configureAccountRefresh();
    configureMoveMoneyForm();
    loadAccounts();
});
